(ns matcher-combinators.cljs-example-test
  (:require [clojure.test :refer [deftest testing is are]]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.core :as c]
            [matcher-combinators.test])
  (:import [goog.Uri]))

(def now-date (js/Date.))
(def now-time (.getTime now-date))
(def a-var (var now-time))
(def uri (goog.Uri.parse "http://www.google.com:80/path?q=query#frag\nmento"))

(deftest basic-examples
  (testing "does it work?"
    (is (match? "foo" "foo"))
    (is (match? 3 3))
    (is (match? nil nil))
    (is (match? :foo :foo))
    (is (match? a-var
                a-var))
    (is (match? 0.1 0.1))
    (is (match? (uuid "00000000-0000-0000-0000-000000000000")
                (uuid "00000000-0000-0000-0000-000000000000")))
    (is (match? uri
                uri))
    (is (match? (goog.Uri.parse "http://www.google.com:80/path?q=query#frag\nmento")
                (goog.Uri.parse "http://www.google.com:80/path?q=query#frag\nmento")))
    (is (match? now-date
                now-date))
    (is (match? now-time
                now-time))
    (is (match? true true))
    (is (match? even? 2))
    (is (match? (cons 1 '())
                (list 1)))
    (is (match? [1]
                [1]))
    (is (match? #{:k}
                #{:k}))
    (is (match? (repeat 1 1)
                [1]))
    (is (match? (take 1 '(1))
                [1]))
    (is (match? {:foo even?}
                {:foo 2
                 :bar 3}))
    (is (match? {:foo even?
                 :baz m/absent}
                {:foo 2
                 :bar 3}))
    (is (match? {:one #"1"}
                {:one "1"}))))

(deftest standalone
  (is (standalone/match? (m/in-any-order [1 2]) [1 2]))
  (is (not (standalone/match? (m/in-any-order [1 2]) [1 3]))))

(deftest partial-standalone
  (testing "using partial version of match?"
    (is ((standalone/match? (m/embeds {:a odd?})) {:a 1 :b 2}))))

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
