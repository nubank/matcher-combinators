(ns matcher-combinators.parser-test
  (:require [midje.sweet :refer :all]
            [midje.experimental :refer [for-all]]
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
       (match (equals-map {:a 10}) {:a 10}))
    => truthy))

(fact "vectors act as equals-seq matchers"
  (fact
    (= (match (equals-seq [10]) [10])
       (match (equals-seq [(equals-value 10)]) [10]))
    => truthy))
