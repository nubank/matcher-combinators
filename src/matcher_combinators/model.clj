(ns matcher-combinators.model)

(defrecord Mismatch [expected actual])
(defrecord Missing  [expected])
(defrecord Unexpected [actual])
(defrecord FailedChecker [form actual])
