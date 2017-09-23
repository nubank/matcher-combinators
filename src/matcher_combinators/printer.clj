(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            [matcher-combinators.model :as model]
            [colorize.core :as colorize])
  (:import [matcher_combinators.model Mismatch Missing Unexpected]))

(defrecord ColorTag [color expression])

(defmulti markup-expression class)

(defmethod markup-expression Mismatch [mismatch]
  (list 'mismatch (->ColorTag :yellow (:expected mismatch)) (->ColorTag :red (:actual mismatch))))

(defmethod markup-expression Missing [missing]
  (list 'missing (->ColorTag :red (:expected missing))))

(defmethod markup-expression Unexpected [unexpected]
  (list 'unexpected (->ColorTag :red (:actual unexpected))))

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

(defn print [expression]
  (with-out-str
    (pprint/with-pprint-dispatch
      print-diff-dispatch
      (pprint/pprint expression))))
