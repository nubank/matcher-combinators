(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            [matcher-combinators.model :as model]
            [colorize.core :as colorize])
  (:import [matcher_combinators.model Mismatch Missing Unexpected FailedPredicate]))

(def ^:dynamic *text-cues-mode*
  "When set to true, printing functions include textual symbols as alternative cues for contextual colors
  (may be useful for visually impaired people)."
  false)

(defrecord ColorTag [color expression])

(defmulti markup-expression class)

(defmethod markup-expression Mismatch [mismatch]
  (list 'mismatch (->ColorTag :yellow (:expected mismatch)) (->ColorTag :red (:actual mismatch))))

(defmethod markup-expression Missing [missing]
  (list 'missing (->ColorTag :red (:expected missing))))

(defmethod markup-expression Unexpected [unexpected]
  (list 'unexpected (->ColorTag :red (:actual unexpected))))

(defmethod markup-expression FailedPredicate [failed-predicate]
  (list 'predicate (->ColorTag :yellow (:form failed-predicate)) (->ColorTag :red (:actual failed-predicate))))

(defmethod markup-expression :default [expression]
  expression)

(def ^:private text-cues-map
  "Map colors to textual symbols that can provide a similar context for visually impaired programmers."
  {:red    "-"
   :yellow "+"})

(defn- write-text-cue-for
  "Writes the text cue that corresponds to the color in question to *out*."
  [in-color]
  (when *text-cues-mode*
    (pprint/write-out
     (-> (:color in-color) text-cues-map (str "  ") symbol))))

(defn colorized-print [in-color]
  (clojure.core/print (colorize/ansi (:color in-color)))
  (write-text-cue-for in-color)
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

(defmacro with-text-cues
  "Evaluates the forms in a context where the var *text-cues-mode* is bound to true."
  [& forms]
  `(binding [*text-cues-mode* true]
     ~@forms))
