(ns matcher-combinators.standalone-test
  (:require [orchestra.spec.test :as spec.test]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.result :as result]
            [matcher-combinators.standalone :as standalone]))

(use-fixtures :once
  (fn [f]
    (spec.test/instrument)
    (f)
    (spec.test/unstrument)))

(def java-set (doto (new java.util.HashSet) (.add 1) (.add 2)))

(deftest test-match
  (testing "parser defaults"
    (is (= :match    (:match/result (standalone/match 37 37))))
    (is (= :match    (:match/result (standalone/match {:a odd?} {:a 1 :b 2}))))
    (is (= :mismatch (:match/result (standalone/match 37 42))))
    (is (= :mismatch (:match/result (standalone/match {:a odd?} {:a 2 :b 2}))))
    (is (= :match (:match/result (standalone/match #{1 2} java-set))))
    (is (= :match (:match/result (standalone/match java-set #{1 2})))))

  (testing "explicit matchers"
    (is (= :match    (:match/result (standalone/match (m/embeds {:a odd?}) {:a 1 :b 2}))))
    (is (= :match    (:match/result (standalone/match (m/in-any-order [1 2]) [1 2]))))
    (is (= :mismatch (:match/result (standalone/match (m/in-any-order [1 2]) [1 3]))))
    (is (= :match (:match/result (standalone/match (m/set-equals [odd? even?]) java-set))))
    (is (= :match (:match/result (standalone/match (m/set-embeds [odd? even?]) java-set)))))

  ;; TODO (dchelimsky,2020-03-11): consider making it a plain datastructure
  (testing ":match/detail binds to a Mismatch object"
    (is (instance? matcher_combinators.model.Mismatch (:mismatch/detail (standalone/match 37 42))))))

(deftest test-match?
  (testing "parser defaults"
    (is (standalone/match? 37 37))
    (is (standalone/match? {:a odd?} {:a 1 :b 2}))
    (is (not (standalone/match? 37 42)))
    (is (not (standalone/match? {:a odd?} {:a 2 :b 2}))))

  (testing "explicit matchers matchers"
    (is (standalone/match (m/embeds {:a odd?}) {:a 1 :b 2}))
    (is (standalone/match? (m/in-any-order [1 2]) [1 2]))
    (is (not (standalone/match? (m/in-any-order [1 2]) [1 3]))))

  (testing "using partial version of match?"
    (is ((standalone/match? (m/embeds {:a odd?})) {:a 1 :b 2}))))
