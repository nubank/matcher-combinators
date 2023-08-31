(ns matcher-combinators.cljs-example-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match? thrown-match?]]
            [matcher-combinators.test-helpers :refer [abs-value-matcher no-match?]])
  (:import [goog.Uri]))

(def gen-any-equatable
  (gen/one-of [gen/any-equatable
               (gen/return (js/Date.))
               (gen/return (var identity))
               (gen/return (.getTime (js/Date.)))
               (gen/return (goog.Uri.parse "http://www.google.com:80/path?q=query#frag\nmento"))]))

(defspec equals-matcher-matches-when-values-are-equal
  {:max-size 10}
  (prop/for-all [v gen-any-equatable]
                (standalone/match? (m/equals v) v)))

(defspec equals-matcher-matches-equal-values-with-partial-application
  {:max-size 10}
  (prop/for-all [v gen-any-equatable]
                ((standalone/match? (m/equals v)) v)))

(deftest standalone-match?
  (testing "with expected and actual"
    (is (standalone/match? (m/in-any-order [1 2]) [1 2]))
    (is (standalone/match? {:a 1 :b m/absent} {:a 1}))
    (is (not (standalone/match? (m/in-any-order [1 2]) [1 3]))))
  (testing "with partial application"
    (let [match-fn (standalone/match? (m/embeds {:a odd?}))]
      (is (match-fn {:a 1 :b 2})))))

(defn bang! [] (throw (ex-info "an exception" {:foo 1 :bar 2})))

(deftest via-matcher
  (testing "normal usage matches"
    (is (standalone/match? (m/via sort [1 2 3])
                           [2 3 1])))
  (testing "`(sort 2)` throws and causes a mismatch"
    (is (not (standalone/match? (m/via sort 2)
                                2))))
  (testing "via + match-with allows pre-processing `actual` before applying matching"
    (is (match? (m/match-with
                  [vector? (fn [expected] (m/via sort expected))]
                  {:payloads [1 2 3]})
                {:payloads (shuffle [3 2 1])}))))

(deftest exception-matching
  (is (thrown-match? ExceptionInfo
                     {:foo 1}
                     (bang!))))

(deftest passing-match
  (is (match? {:a 2} {:a 2 :b 1})))

(deftest pred-match
  (is (match? #{odd?}
              #{1}))
  (is (match? #{(m/pred odd?)}
              #{1})))

(comment
  (deftest match?-no-actual-arg
    (testing "fails with nice message when you don't provide an `actual` arg to `match?`"
      (is (match? 1)
          :in-wrong-place)))

  (deftest failing-match
    (is (match? 1 2)))

  (deftest thrown-match?-no-actual-arg
    (testing "fails with nice message when you don't provide an `actual` arg to `thrown-match?`"
      (is (thrown-match? ExceptionInfo {:a 1})
          :in-wrong-place))))

(deftest match-with-test
  (testing "Example numeric test-case"
    (is (match? (m/match-with [number? (m/within-delta 0.05)] 1) 0.99)))

  (testing "maps"
    (testing "passing case with equals override"
      (is (match? (m/match-with [map? m/equals]
                                {:a :b})
                  {:a :b})))
    (testing "failing case with equals override"
      (is (no-match? (m/match-with [map? m/equals]
                                   {:a :b})
                     {:a :b :d :e})))
    (testing "passing case multiple scopes"
      (is (match?
            {:o (m/match-with [map? m/equals]
                              {:a
                               (m/match-with [map? m/embeds]
                                             {:b :c})})}
            {:o {:a {:b :c :d :e}}
             :p :q})))
    (testing "using `absent` matcher"
      (is (match? (m/match-with [map? m/equals]
                                {:a m/absent
                                 :b :c})
                  {:b :c}))
      (is (match? (m/match-with [map? m/embeds]
                                {:a m/absent})
                  {:b :c}))))

  (testing "sets"
    (is (match?
          (m/match-with [set? m/embeds]
                        #{1})
          #{1 2}))

    (is (match?
          (m/match-with [set? m/embeds]
                        #{(m/pred odd?)})
          #{1 2})))

  (let [matcher (m/match-with [pos? abs-value-matcher
                               integer? m/equals]
                              5)]
    (is (match? matcher 5))
    (is (match? matcher -5))))
