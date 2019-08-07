(ns matcher-combinators.test-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer [build-match-assert]]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as m])
  (:import [clojure.lang ExceptionInfo]))

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

(defn bang! [] (throw (ex-info "an exception" {:foo 1 :bar 2})))

(deftest exception-matching
  (is (thrown-match? ExceptionInfo
                     {:foo 1}
                     (bang!))))

(comment
  (deftest match?-no-actual-arg
    (testing "fails with nice message when you don't provide an `actual` arg to `match?`"
      (is (match? 1)
          :in-wrong-place)))

  (deftest thrown-match?-no-actual-arg
    (testing "fails with nice message when you don't provide an `actual` arg to `thrown-match?`"
      (is (thrown-match? ExceptionInfo {:a 1})
          :in-wrong-place))))

(defn greater-than-matcher [expected-long]
  (core/->PredMatcher
   (fn [actual] (> actual expected-long))))

(deftest match-with-test
  (is (match-with? {java.lang.Long greater-than-matcher}
                   4
                   5)))

(clojure.pprint/pprint (macroexpand `(build-match-assert foo? {java.lang.Long greater-than-matcher})))

(defmethod clojure.test/assert-expr 'baz? [msg form]
  (build-match-assert 'baz? {java.lang.Long greater-than-matcher} msg form))

(deftest match-baz-test
  (is (baz? 4 5)))
