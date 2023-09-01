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

(defn- mismatch? [expr]
  (or (instance? EllisionMarker expr)
      (instance? EmptyMarker expr)
      (instance? Mismatch expr)
      (instance? Missing expr)
      (instance? Unexpected expr)
      (instance? InvalidMatcherType expr)
      (instance? InvalidMatcherContext expr)
      (instance? TypeMismatch expr)))

(defn- mismatch+? [x]
  (or (mismatch? x)
      (= :mismatch-map (:mismatch (meta x)))
      (= :mismatch-sequence (:mismatch (meta x)))))

(defn abbreviated [expr]
  (walk/prewalk (fn [x]
                  (cond (mismatch? x)
                        x

                        (= :mismatch-map (:mismatch (meta x)))
                        ;; keep only mismatched data from the mismatched map
                        (into {} (filter (fn [[_k v]] (mismatch+? v))) x)

                        (= :mismatch-sequence (:mismatch (meta x)))
                        ;; keep only mismatched data from the sequence
                        (#'core/type-preserving-mismatch (empty x) (filter mismatch+? x))

                        :else
                        x))
                expr))

(defn pretty-print [expr]
  (pprint/with-pprint-dispatch
    print-diff-dispatch
    (pprint/pprint (if config/*use-abbreviation*
                     ((comp abbreviated with-ellision-marker) expr)
                     expr))))

(defn as-string [value]
  (with-out-str
    (pretty-print value)))
