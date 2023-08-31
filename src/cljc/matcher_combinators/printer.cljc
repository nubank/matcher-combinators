(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
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

(defn mismatch? [expr]
  (or (instance? Mismatch expr)
      (instance? Missing expr)
      (instance? Unexpected expr)
      (instance? InvalidMatcherType expr)
      (instance? InvalidMatcherContext expr)
      (instance? TypeMismatch expr)))

(defn mismatch+? [x]
  (or (matcher-combinators.printer/mismatch? x)
      (= :mismatch-map (:mismatch (meta x)))
      (= :mismatch-sequence (:mismatch (meta x)))))

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

(defn redacted [expr]
  (walk/prewalk (fn [x]
                  (cond (mismatch? x)
                        x

                        (= :mismatch-map (:mismatch (meta x)))
                        (into {} (filter (fn [[_k v]] (mismatch+? v))) x)

                        (= :mismatch-sequence (:mismatch (meta x)))
                        (#'core/type-preserving-mismatch (empty x) (filter mismatch+? x))

                        :else
                        x))
                expr))

(def ^{:dynamic true
       :doc "thread-local way to control, via `binding`, the redacting of fully-matched data-structures in the matcher-combinator output"}
  *use-redaction*
  false)

(defn- set-use-redaction! [v]
  #?(:clj (alter-var-root #'*use-redaction* (constantly v))
     :cljs (set! *use-redaction* v)))

(defn enable-redaction!
  "Thread-global way to enable the redaction of fully-matched data-structures in matcher-combinator output."
  []
  (set-use-redaction! true))

(defn disable-redaction!
  "Thread-global way to disable the redaction of fully-matched data-structures in matcher-combinator output."
  []
  (set-use-redaction! false))

(defn pretty-print [expr]
  (pprint/with-pprint-dispatch
    print-diff-dispatch
    (pprint/pprint (cond-> expr
                     *use-redaction*
                     redacted))))

(defn as-string [value]
  (with-out-str
    (pretty-print value)))

(comment
  (require '[clojure.test :refer [is]]
           '[matcher-combinators.test :refer [match?]])
  (is (match? {:name/first "Alfredo"
               :f [1 2 3]}
              {:name/first  "Afredo"
               :f [3 2 1]
               :name/last   "da Rocha Viana"
               :name/suffix "Jr."})))
