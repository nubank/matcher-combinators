(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            [matcher-combinators.model :as model]
            [colorize.core :as colorize])
  (:import [matcher_combinators.model Mismatch Missing Unexpected]))

(defrecord InColor [color expression])

(defmulti print-diff-dispatch class)

(defmethod print-diff-dispatch Mismatch [mismatch]
  (print-diff-dispatch
    (list 'mismatch (->InColor :yellow (:expected mismatch)) (->InColor :red (:actual mismatch)))))

(defmethod print-diff-dispatch Missing [missing]
  (print-diff-dispatch
    (list 'missing (->InColor :yellow (:expected missing)))))

(defmethod print-diff-dispatch Unexpected [unexpected]
  (print-diff-dispatch
    (list 'unexpected (->InColor :red (:actual unexpected)))))

(defmethod print-diff-dispatch InColor [in-color]
  (clojure.core/print (colorize/ansi (:color in-color)))
  (pprint/write-out (:expression in-color))
  (clojure.core/print (colorize/ansi :reset)))

(defmethod print-diff-dispatch :default [expression]
  (pprint/simple-dispatch expression))

(defn print [expression]
  (with-out-str
    (pprint/with-pprint-dispatch
      print-diff-dispatch
      (pprint/pprint expression))))
