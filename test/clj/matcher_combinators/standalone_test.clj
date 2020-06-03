(ns matcher-combinators.standalone-test
  (:require [orchestra.spec.test :as spec.test]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [matcher-combinators.specs]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test-helpers :as test-helpers]
            [matcher-combinators.result :as result]
            [matcher-combinators.standalone :as standalone]))

(use-fixtures :once test-helpers/instrument)

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

  (testing ":match/detail binds to a Mismatch object"
    (is (instance? matcher_combinators.model.Mismatch (:mismatch/detail (standalone/match 37 42))))))

(deftest test-indicates-match?
  (testing "with core match result"
    (is (standalone/indicates-match? {::result/type :match
                                      ::result/value nil
                                      ::result/weight 0}))
    (is (not (standalone/indicates-match? {::result/type :mismatch
                                           ::result/value nil
                                           ::result/weight 1}))))
  (testing "with standalone match result"
    (is (standalone/indicates-match? {:match/result :match}))
    (is (not (standalone/indicates-match? {:match/result :mismatch})))))

(deftest test-match?
  (testing "parser defaults"
    (is (standalone/match? 37 37))
    (is (standalone/match? {:a odd?} {:a 1 :b 2}))
    (is (not (standalone/match? 37 42)))
    (is (not (standalone/match? {:a odd?} {:a 2 :b 2}))))

  (testing "explicit matchers"
    (is (standalone/match? (m/embeds {:a odd?}) {:a 1 :b 2}))
    (is (standalone/match? (m/in-any-order [1 2]) [1 2]))
    (is (not (standalone/match? (m/in-any-order [1 2]) [1 3])))))
