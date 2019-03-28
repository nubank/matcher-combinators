(ns matcher-combinators.model)

(defrecord Mismatch [expected actual])
(defrecord Missing  [expected])
(defrecord Unexpected [actual])
(defrecord InvalidMatcherType [provided expected-type-msg])
(defrecord FailedPredicate [form actual])
(defrecord TypeMismatch [expected actual])
