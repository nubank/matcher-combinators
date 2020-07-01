(ns matcher-combinators.test-test
  (:require [clojure.test :refer [use-fixtures deftest testing is are]]
            [matcher-combinators.test :refer :all]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test-helpers :as test-helpers :refer [greater-than-matcher]])
  (:import [clojure.lang ExceptionInfo]))

(use-fixtures :once test-helpers/instrument)

(def example-matcher {:username string?
                      :account  {:id        integer?
                                 :open-date "25-02-1997"}})

(def example-actual {:username "barbara"
                     :device   "android"
                     :account  {:id         1
                                :open-date  "25-02-1997"
                                :extra-data 'blah}})

(def match-data {:foo 1 :bar 2})

(defmacro shhh!
  "Evals and returns the value of body without reporting failures."
  [& body]
  `(with-redefs [clojure.test/do-report (constantly nil)]
     ~@body))

(deftest basic-matching
  (is (match? example-matcher example-actual)
      "In 'match?', the matcher argument comes first")
  (is (match? (m/equals example-matcher)
              (dissoc example-actual :device))
      "wrapping the matcher in 'equals' means the top level of 'actual' must have the exact same key/values")
  (is (true? (is (match? 1 1)))
      "match? should return true for a :match")
  (is (false? (shhh! (is (match? 1 2))))
      "match? should return false for a :mismatch")
  (is (match? 1 1))
  (is (match? (m/equals [1 odd?]) [1 3]))
  (is (match? {:a {:b odd?}}
              {:a {:b 1}})
      "Predicates can be used in matchers")
  (is (match? {:a {:b 1}} {:a {:b 1 :c 2}}))
  (are [data-matcher data]
       (match? data-matcher (with-redefs [match-data data] match-data))
    {:foo 4 :bar 5} {:foo 4 :bar 5}
    {:foo 2 :bar 3} {:foo 2 :bar 3}))

(defn bang! [] (throw (ex-info "an exception" match-data)))

(deftest exception-matching
  (testing "is"
    (is (thrown-match? ExceptionInfo {:foo 1} (bang!))))
  (testing "is with default ExceptionInfo class"
    (is (thrown-match? {:foo 1} (bang!))))
  (testing "are"
    (are [data-matcher]
         (thrown-match? data-matcher (bang!))
      {:foo 1}
      {:bar 2}))
  (testing "are with redefs"
    (are [data-matcher data]
         (thrown-match? ExceptionInfo data-matcher (with-redefs [match-data data] (bang!)))
      {:foo 4} {:foo 4 :bar 5}
      {:foo 2} {:foo 2 :bar 3}
      {:bar 3} {:foo 2 :bar 3})))

(comment
  (deftest match?-no-actual-arg
    (testing "fails with nice message when you don't provide an `actual` arg to `match?`"
      (is (match? 1)
          :in-wrong-place)))

  (deftest thrown-match?-incorrect-args
    (testing "fails with nice message when you don't provide an `actual` arg"
      (is (thrown-match? ExceptionInfo {:a 1})
          :in-wrong-place))
    (testing "fails with a nice message when you don't provide enough arguments"
      (is (thrown-match? {:a 1})
          :in-wrong-place))
    (testing "fails with a nice message when you provide too many arguments"
      (is (thrown-match? ExceptionInfo {:a 1} (bang!) :extra-arg)))))

(deftest match-with-test
  (is (match-with? {java.lang.Long greater-than-matcher}
                   4
                   5)))

(defmethod clojure.test/assert-expr 'match-greather-than? [msg form]
  (build-match-assert 'match-greather-than? {java.lang.Long greater-than-matcher} msg form))

(deftest match-greater-than-test
  (is (match-greather-than? 4 5)))

(deftest match-equals-test
  (is (match-equals? {:a 1}
                     {:a 1})))

(deftest match-equals-single-eval
  (testing "in presence of macro expansion, arguments to match-equals? are only evaluated once"
    (let [arg-count     (atom 0)
          matcher-count (atom 0)]
      (is (match-equals? (do (swap! matcher-count inc)
                             {:a 1})
                         (do (swap! arg-count inc)
                             {:a 1})))
      (is (= 1 @matcher-count @arg-count)))))

(deftest match-roughly-test
  (is (match-roughly? 0.1
                      {:a 1 :b 3.0}
                      {:a 1 :b 3.05}))
  (is (match-roughly? 0.1M
                      {:a 1 :b 3.0}
                      {:a 1 :b 3.05})))
