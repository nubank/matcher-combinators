(ns matcher-combinators.test-test
  (:require [matcher-combinators.test]
            [matcher-combinators.core :as core]
            [clojure.test :refer :all]))

(deftest matchers)

(deftest basic-matching
  (is (match? 1 1))
  (is (match? (core/equals-sequence [1 odd?]) [1 3]))
  (is (match? {:a {:b odd?}} {:a {:b 1}}))
  (is (match? {:a {:b 1}} {:a {:b 1}})))
