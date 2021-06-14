(ns matcher-combinators.printer
  (:refer-clojure :exclude [print])
  (:require [clojure.pprint :as pprint]
            #?(:clj [flare.string])
            #?(:clj  [matcher-combinators.model]
               :cljs [matcher-combinators.model :refer [ExpectedMismatch
                                                        Mismatch
                                                        Missing
                                                        Unexpected
                                                        TypeMismatch
                                                        InvalidMatcherContext
                                                        InvalidMatcherType]])
            [matcher-combinators.result :as result]
            [matcher-combinators.ansi-color :as ansi-color])
  #?(:clj
     (:import [matcher_combinators.model ExpectedMismatch Mismatch Missing
               Unexpected TypeMismatch InvalidMatcherContext InvalidMatcherType])))

(defrecord ColorTag [color expression])

(defmulti markup-expression type)

(defn- build-diff [expected actual]
  (if (and (string? expected) (string? actual))
    (let [expected-diff (flare.string/diff-tuples->string
                          (flare.string/diff-tuples expected actual))
          actual-diff   (flare.string/diff-tuples->string
                          (flare.string/diff-tuples actual expected))]
      [expected-diff actual-diff])
    [expected actual]))

(defmethod markup-expression Mismatch [{:keys [expected actual]}]
  (let [[expected-out actual-out] #?(:clj  (build-diff expected actual)
                                     :cljs [expected actual])] ;; TODO: figure out how to use flare with cljs
    (list 'mismatch
          (->ColorTag :yellow expected-out)
          (->ColorTag :red actual-out))))

(defmethod markup-expression ExpectedMismatch [mismatch]
  (list 'mismatch
        (->ColorTag :yellow (symbol "expected mismatch from: "))
        (->ColorTag :yellow (:expected mismatch))
        (->ColorTag :red (:actual mismatch))))

(defmethod markup-expression Missing [missing]
  (list 'missing (->ColorTag :red (:expected missing))))

(defmethod markup-expression Unexpected [unexpected]
  (list 'unexpected (->ColorTag :red (:actual unexpected))))

(defmethod markup-expression TypeMismatch [mismatch]
  (list 'mismatch
        (->ColorTag :yellow (type (:expected mismatch)))
        (->ColorTag :red (type (:actual mismatch)))))

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

(defn pretty-print [expr]
  (pprint/with-pprint-dispatch
    print-diff-dispatch
    (pprint/pprint expr)))

(defn as-string [value]
  (with-out-str
    (pretty-print value)))
