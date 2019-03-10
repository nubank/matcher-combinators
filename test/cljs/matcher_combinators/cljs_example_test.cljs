(ns matcher-combinators.cljs-example-test
  (:require [clojure.test :refer [deftest testing is are]]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.core :as c]
            [matcher-combinators.test]))

(def now-date (js/Date.))
(def now-time (.getTime now-date))
(def a-var (var now-time))

(deftest foo-test
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
    (is (match? {:one #"1"}
                {:one "1"}))))


(defn bang! [] (throw (ex-info "an exception" {:foo 1 :bar 2})))

(deftest exception-matching
  (is (thrown-match? ExceptionInfo
                     {:foo 1}
                     (bang!))))
