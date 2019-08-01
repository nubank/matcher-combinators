(ns matcher-combinators.parser-test
  (:require [midje.sweet :refer :all :exclude [exactly contains]]
            [midje.experimental :refer [for-all]]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :refer :all]
            [matcher-combinators.core :as core]
            [clojure.test.check.generators :as gen]
            [matcher-combinators.model :as model]))

(def gen-big-decimal
  (gen/fmap (fn [[integral fractional]]
              (BigDecimal. (str integral "." fractional)))
            (gen/tuple gen/int gen/pos-int)))

(def gen-big-int
  (gen/fmap #(* 1N %) gen/int))

(def gen-java-integer
  (gen/fmap #(Integer. %) gen/int))

(def gen-float
  (gen/fmap #(float %) gen/int))

(def gen-short
  (gen/fmap short gen/int))

(def gen-var (gen/elements (vals (ns-interns 'clojure.core))))

(def gen-scalar (gen/one-of [gen-java-integer
                             gen/int ;; really a Long
                             gen-short
                             gen/string
                             gen/symbol
                             gen-float
                             gen/double
                             gen/symbol-ns
                             gen/keyword
                             gen/boolean
                             gen/ratio
                             gen-big-decimal
                             gen-big-int
                             gen/char
                             gen/bytes
                             gen-var]))

(defn gen-distinct-pair [element-generator]
  (gen/such-that (fn [[i j]] (not= i j)) (gen/tuple element-generator)))

(def gen-scalar-pair
  (gen-distinct-pair gen-scalar))

(facts "scalar values act as equals matchers"
  (for-all [i gen-scalar]
           {:num-tests 50}
           (core/match i i) => (core/match (equals i) i))

  (for-all [[i j] gen-scalar-pair]
           {:num-tests 50}
           (core/match i j) => (core/match (equals i) j)))

(fact "maps act as equals matcher"
  (fact
   (= (core/match (equals {:a (equals 10)}) {:a 10})
      (core/match (equals {:a 10}) {:a 10})
      (core/match {:a 10} {:a 10}))
    => truthy))

(fact "vectors act as equals matchers"
  (fact
   (= (core/match (equals [(equals 10)]) [10])
      (core/match (equals [10]) [10])
      (core/match [10] [10]))
    => truthy))

(fact "lists also act as equals matchers"
  (fact
   (= (core/match (equals [(equals 10)]) [10])
      (core/match (equals '(10)) [10])
      (core/match '(10) [10])) => truthy))

(fact "`nil` is parsed as an equals"
  (fact
   (= (core/match (equals nil) nil)
      (core/match nil nil)) => truthy))

(fact "java classes are parsed as an equals"
  (fact
   (= (core/match (equals java.lang.String) java.lang.String)
      (core/match java.lang.String java.lang.String)) => truthy))
