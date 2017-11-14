(ns matcher-combinators.parser-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.parser]
            [matcher-combinators.core :refer :all]
            [clojure.test.check.generators :as gen]
            [matcher-combinators.model :as model]))

(def gen-big-decimal
  (gen/fmap (fn [[integral fractional]]
              (BigDecimal. (str integral "." fractional)))
            (gen/tuple gen/int gen/pos-int)))

(def gen-big-int
  (gen/fmap #(* 1N %) gen/int))

(def gen-scalar (gen/one-of [gen/int
                             gen/string
                             gen/symbol
                             gen/symbol-ns
                             gen/keyword
                             gen/boolean
                             gen/ratio
                             gen-big-decimal
                             gen-big-int
                             gen/char]))

(defn gen-distinct-pair [element-generator]
  (gen/such-that (fn [[i j]] (not= i j)) (gen/tuple element-generator)))

(def gen-scalar-pair
  (gen-distinct-pair gen-scalar))

(declare gen-matcher-expression)

(def nested-vector-of-boolean (gen/recursive-gen gen/vector gen/boolean))

(gen/sample nested-vector-of-boolean 20)

(def gen-compound (fn [inner]
                     (gen/one-of
                       [(gen/map gen-scalar inner)
                        (gen/vector inner)])))

(def gen-matcher-expression (gen/recursive-gen gen-compound gen-scalar))

(def gen-map (gen/map gen-scalar gen-matcher-expression))

(def gen-vector (gen/vector gen-matcher-expression))

(facts "scalar values act as equals-value matchers"
  (for-all [i gen-scalar]
    {:num-tests 50}
    (match i i) => (match (equals-value i) i))

  (for-all [[i j] gen-scalar-pair]
    {:num-tests 50}
    (match i j) => (match (equals-value i) j)))

(fact "maps act as equals-map matchers"
  (fact
    (= (match (equals-map {:a (equals-value 10)}) {:a 10})
       (match (equals-map {:a 10}) {:a 10})
       (match {:a 10} {:a 10}))
    => truthy)

  (fact "maps act as equals-map matchers"
    (for-all [i gen-map]
      {:num-tests 50}
      (match i i) => (match (equals-map i) i))

    (for-all [[i j] (gen-distinct-pair gen-map)]
      {:num-tests 50}
      (match i j) => (match (equals-map i) j))))

(fact "vectors act as equals-sequence matchers"
  (fact
    (= (match (equals-sequence [(equals-value 10)]) [10])
       (match [(equals-value 10)] [10])
       (match [10] [10]))
    => truthy)

  (fact "vectors act as equals-sequence matchers"
    (for-all [v gen-vector]
      {:num-tests 50}
      (match v v) => (match (equals-sequence v) v))

    (for-all [[i j] (gen-distinct-pair gen-vector)]
      {:num-tests 50}
      (match i j) => (match (equals-map i) j))))
