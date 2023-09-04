(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            [matcher-combinators.config :as config]
            [matcher-combinators.core :as core]
            #?(:clj  [matcher-combinators.model]
               :cljs [matcher-combinators.model :refer [ExpectedMismatch
                                                        Mismatch
                                                        Missing
                                                        Unexpected
                                                        TypeMismatch
                                                        InvalidMatcherContext
                                                        InvalidMatcherType]])
            [clojure.walk :as walk]
            [matcher-combinators.ansi-color :as ansi-color])
  #?(:clj
     (:import [matcher_combinators.model ExpectedMismatch Mismatch Missing
               Unexpected TypeMismatch InvalidMatcherContext InvalidMatcherType])))

(defrecord ColorTag [color expression])

(defmulti markup-expression type)

(defmethod markup-expression Mismatch [{:keys [expected actual]}]
  (list 'mismatch
        (list 'expected (->ColorTag :yellow expected))
        (list 'actual (->ColorTag :red actual))))

(defmethod markup-expression ExpectedMismatch [{:keys [actual expected]}]
  (list 'mismatch
        (->ColorTag :yellow (symbol "expected mismatch from: "))
        (->ColorTag :yellow expected)
        (list 'actual (->ColorTag :red actual))))

(defmethod markup-expression Missing [missing]
  (list 'missing (->ColorTag :red (:expected missing))))

(defmethod markup-expression Unexpected [unexpected]
  (list 'unexpected (->ColorTag :red (:actual unexpected))))

(defmethod markup-expression TypeMismatch [{:keys [actual expected]}]
  (list 'mismatch
        (list 'expected (->ColorTag :yellow (type expected)))
        (list 'actual (->ColorTag :red (type actual)))))

(defmethod markup-expression InvalidMatcherType [invalid-type]
  (list 'invalid-matcher-input
        (->ColorTag :yellow (:expected-type-msg invalid-type))
        (->ColorTag :red (:provided invalid-type))))

(defmethod markup-expression InvalidMatcherContext [invalid-context]
  (list 'invalid-matcher-context
        (->ColorTag :red (:message invalid-context))))

(defmethod markup-expression :default [expression]
  expression)

(defn colorized-print [{:keys [color expression]}]
  (if color
    #?(:clj  (do (ansi-color/set-color color)
                 (pprint/write-out expression)
                 (ansi-color/reset))
       :cljs (pprint/write-out (ansi-color/style expression color)))
    (pprint/write-out expression)))

(defn print-diff-dispatch [expression]
  (let [markup (markup-expression expression)]
    (if (instance? ColorTag markup)
      (colorized-print markup)
      (pprint/simple-dispatch markup))))

(defrecord EllisionMarker [])
(defmethod markup-expression EllisionMarker [_] '...)
(def ellision-marker (EllisionMarker.))

(defrecord EmptyMarker [])
(defmethod markup-expression EmptyMarker [_] (symbol ""))
(def empty-marker (EmptyMarker.))

(defn with-ellision-marker
  "Include `...` in mismatch data-structure to show that the match output has
  been abbreviated"
  [expr]
  (cond (or (sequential? expr)
            (set? expr))
        (conj expr ellision-marker)

        (and (map? expr) (not (core/non-internal-record? expr)))
        (assoc expr ellision-marker empty-marker)

        :else
        expr))

(defn- complete-mismatch? [expr]
  (or (instance? EllisionMarker expr)
      (instance? EmptyMarker expr)
      (instance? Missing expr)
      (instance? Unexpected expr)
      (instance? InvalidMatcherType expr)
      (instance? InvalidMatcherContext expr)
      (instance? TypeMismatch expr)))

(defn- mismatch+? [x]
  (or (complete-mismatch? x)
      (instance? Mismatch x)
      (= :mismatch-map (:mismatch (meta x)))
      (= :mismatch-sequence (:mismatch (meta x)))))

(defn- prewalk-with-skip
  "A specialization of clojure.prewalk that adds a predicate that stops
  recursion when satisfied."
  [f skip? form]
  (cond (skip? form)
        form

        (list? form)
        (apply list (map f form))

        #?(:clj (instance? clojure.lang.IMapEntry form)
           :cljs (map-entry? form))
        #?(:clj (vec (map f form))
           :cljs (MapEntry. (f (key form)) (f (val form)) nil))

        (seq? form)
        (doall (map f form))

        #?(:clj (instance? clojure.lang.IRecord form)
           :cljs (record? form))
        (reduce (fn [r x] (conj r (f x))) form form)

        (coll? form)
        (into (empty form) (map f form))

        :else
        form))

(defn abbreviate-expr [expr]
  (cond (= :mismatch-map (:mismatch (meta expr)))
        ;; keep only mismatched data from the mismatched map
        (into {} (filter (fn [[_k v]] (mismatch+? v))) expr)

        (= :mismatch-sequence (:mismatch (meta expr)))
        ;; keep only mismatched data from the sequence
        (#'core/type-preserving-mismatch (empty expr) (filter mismatch+? expr))

        :else
        expr))

(defn abbreviated [expr]
  (prewalk-with-skip abbreviated complete-mismatch? (abbreviate-expr expr)))

(defn pretty-print [expr]
  (pprint/with-pprint-dispatch
    print-diff-dispatch
    (pprint/pprint (if config/*use-abbreviation*
                     ((comp abbreviated with-ellision-marker) expr)
                     expr))))

(defn as-string [value]
  (with-out-str
    (pretty-print value)))
