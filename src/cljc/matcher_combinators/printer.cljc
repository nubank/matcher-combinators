(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            #?(:clj  [matcher-combinators.model]
               :cljs [matcher-combinators.model :refer [ExpectedMismatch
                                                        Mismatch
                                                        Missing
                                                        Unexpected
                                                        TypeMismatch
                                                        InvalidMatcherContext
                                                        InvalidMatcherType]])
            [matcher-combinators.ansi-color :as ansi-color])
  #?(:clj
     (:import [matcher_combinators.model Match ExpectedMismatch Mismatch Missing
               Unexpected TypeMismatch InvalidMatcherContext InvalidMatcherType])))

(def ^:dynamic *print-summary* false)

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

(defmethod markup-expression Match [{:keys [actual]}]
  actual)

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

; Could this lead to a Stackoverflow?
(defn filter-expr [action expr]
  (cond
    (instance? Match expr) (case action
                             :redact 'matched
                             :drop   nil
                             :keep   expr)
    ;; TODO if into isn't lazy how to return a lazy map that makes transformations over the map?
    (map? expr) (or (not-empty (into {} (mapcat (fn [[k v]]
                                                  (when-let [new-val (filter-expr :drop v)]
                                                    [[k new-val]]))
                                                expr)))
                    ; TODO is this ok or should I do it based on action?
                    'matched)
    (vector? expr) (vec (map (partial filter-expr :redact) expr))
    (list? expr) (map (partial filter-expr :redact) expr)
    :else expr))

(defn pretty-print [expr]
  (pprint/with-pprint-dispatch
    print-diff-dispatch
    (pprint/pprint (if *print-summary*
                     (filter-expr :keep expr)
                     expr))))

(defn as-string [value]
  (with-out-str
    (pretty-print value)))
