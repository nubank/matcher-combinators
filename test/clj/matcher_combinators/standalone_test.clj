(ns matcher-combinators.standalone-test
  (:require [orchestra.spec.test :as spec.test]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [matcher-combinators.specs]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test-helpers :as test-helpers]
            [matcher-combinators.result :as result]
            [matcher-combinators.standalone :as standalone]))

(use-fixtures :once test-helpers/instrument)

(deftest test-match
  (testing "parser defaults"
    (is (= :match    (:match/result (standalone/match 37 37))))
    (is (= :match    (:match/result (standalone/match {:a odd?} {:a 1 :b 2}))))
    (is (= :mismatch (:match/result (standalone/match 37 42))))
    (is (= :mismatch (:match/result (standalone/match {:a odd?} {:a 2 :b 2})))))

  (testing "explicit matchers"
    (is (= :match    (:match/result (standalone/match (m/embeds {:a odd?}) {:a 1 :b 2}))))
    (is (= :match    (:match/result (standalone/match (m/in-any-order [1 2]) [1 2]))))
    (is (= :mismatch (:match/result (standalone/match (m/in-any-order [1 2]) [1 3])))))

  (testing ":match/detail binds to a Mismatch object"
    (is (instance? matcher_combinators.model.Mismatch (:mismatch/detail (standalone/match 37 42))))))

(deftest test-match?
  (testing "with match result"
    (testing "with core match result"
      (is (standalone/match? {::result/type :match
                              ::result/value nil
                              ::result/weight 0}))
      (is (not (standalone/match? {::result/type :mismatch
                                   ::result/value nil
                                   ::result/weight 1}))))

    (testing "with standalone match result"
      (is (standalone/match? {:match/result :match}))
      (is (not (standalone/match? {:match/result :mismatch})))))

  (testing "with expected and actual"
    (testing "parser defaults"
      (is (standalone/match? 37 37))
      (is (standalone/match? {:a odd?} {:a 1 :b 2}))
      (is (not (standalone/match? 37 42)))
      (is (not (standalone/match? {:a odd?} {:a 2 :b 2}))))

    (testing "explicit matchers"
      (is (standalone/match? (m/embeds {:a odd?}) {:a 1 :b 2}))
      (is (standalone/match? (m/in-any-order [1 2]) [1 2]))
      (is (not (standalone/match? (m/in-any-order [1 2]) [1 3]))))))
