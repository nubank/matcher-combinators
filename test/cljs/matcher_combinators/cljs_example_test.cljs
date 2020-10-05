(ns matcher-combinators.cljs-example-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.core :as c]
            [matcher-combinators.test]
            [matcher-combinators.test-helpers :as helpers])
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
