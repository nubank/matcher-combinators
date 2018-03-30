(ns matcher-combinators.test-test
  (:require [matcher-combinators.test]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as m]
            [clojure.test :refer :all]))

(def example-matcher {:username string?
                      :account  {:id        integer?
                                 :open-date "25-02-1997"}})

(def example-actual {:username "barbara"
                     :device   "android"
                     :account  {:id         1
                                :open-date  "25-02-1997"
                                :extra-data 'blah}})

(deftest basic-matching
  (is (match? example-matcher example-actual)
      "In 'match?', the matcher argument comes first")
  (is (match? (m/equals example-matcher)
              (dissoc example-actual :device))
      "wrapping the matcher in 'equals' means the top level of 'actual'
      must have the exact same key/values")
  (is (match? 1 1))
  (is (match? (m/equals [1 odd?]) [1 3]))
  (is (match? {:a {:b odd?}}
              {:a {:b 1}})
      "Predicates can be used in matchers")
  (is (match? {:a {:b 1}} {:a {:b 1 :c 2}})))

#_(deftest equals-matching
  (is (match-equals? {:a 1}
                     {:a 1 :b 2})
      "matcher where every default parser is `equals`"))
