(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            [matcher-combinators.model :as model]
            [colorize.core :as colorize])
  (:import [matcher_combinators.model Mismatch Missing Unexpected FailedPredicate InvalidMatcherType]))

(defrecord ColorTag [color expression])

(defmulti markup-expression class)

(defmethod markup-expression Mismatch [mismatch]
  (list 'mismatch
        (->ColorTag :yellow (:expected mismatch))
        (->ColorTag :red (:actual mismatch))))

(defmethod markup-expression Missing [missing]
  (list 'missing (->ColorTag :red (:expected missing))))

(defmethod markup-expression Unexpected [unexpected]
  (list 'unexpected (->ColorTag :red (:actual unexpected))))

(defmethod markup-expression FailedPredicate [failed-predicate]
  (list 'predicate
        (->ColorTag :yellow (:form failed-predicate))
        (->ColorTag :red (:actual failed-predicate))))

(defmethod markup-expression InvalidMatcherType [invalid-type]
  (list 'invalid-matcher-input
        (->ColorTag :yellow (:expected-type-msg invalid-type))
        (->ColorTag :red (:provided invalid-type))))

(defmethod markup-expression :default [expression]
  expression)

(defn colorized-print [in-color]
  (clojure.core/print (colorize/ansi (:color in-color)))
  (pprint/write-out (:expression in-color))
  (clojure.core/print (colorize/ansi :reset)))

(defn print-diff-dispatch [expression]
  (let [markup (markup-expression expression)]
    (if (instance? ColorTag markup)
      (colorized-print markup)
      (pprint/simple-dispatch markup))))

(defn pretty-print [expr]
  (pprint/with-pprint-dispatch
    print-diff-dispatch
    (pprint/pprint expr)))

(defn as-string [expr]
  (with-out-str
    (pretty-print expr)))
