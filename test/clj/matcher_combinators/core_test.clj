(ns matcher-combinators.core-test
  (:refer-clojure :exclude [any?])
  (:require [clojure.string :as str]
            [clojure.test :refer [are deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.test :refer [match?]])
  (:import (clojure.lang Associative)))

(defn any? [_x] true)

(defspec equals-matcher-matches-when-values-are-equal
  {:max-size 10}
  (prop/for-all [v gen/any-equatable]
    (standalone/match? (matchers/equals v) v)))

(defspec equals-matcher-mismatches-when-scalar-values-are-not-equal
  {:max-size 10}
  (prop/for-all [[a b] (gen/such-that (fn [[a b]] (not= a b))
                                      (gen/tuple gen/simple-type-equatable
                                                 gen/simple-type-equatable))]
    (standalone/match?
     {::result/value (model/->Mismatch a b)}
     (core/match (matchers/equals a) b))))

(deftest map-matchers-support-map-like-actual-values
  (let [map-like (reify Associative
                   (seq [_] (map identity {:a 1}))
                   (valAt [_ k] (get {:a 1} k))
                   (valAt [_ k _] (get {:a 1} k))
                   (equiv [_ _] false)
                   (cons [_ _]))]
    (testing "map-like test value associative, but not a map or sequential"
      (is (associative? map-like))
      (is (not (map? map-like)))
      (is (not (sequential? map-like))))
    (testing "embeds"
      (is (core/indicates-match?
           (core/match (matchers/embeds {:a 1}) map-like)))
      (is (not (core/indicates-match?
                (core/match (matchers/embeds {:a 2}) map-like))))
      (is (not (core/indicates-match?
                (core/match (matchers/embeds {:b 1}) map-like)))))))

(defspec map-matchers-mismatches-when-one-key-has-a-mismatched-value
  {:max-size 10}
  (prop/for-all [expected (gen/such-that not-empty (gen/map gen/keyword gen/small-integer))
                 m        (gen/elements [matchers/equals matchers/embeds])]
    (let [k      (first (keys expected))
          actual (update expected k inc)
          res    (core/match (m expected) actual)]
      (standalone/match?
       {::result/type  :mismatch
        ::result/value (assoc actual k (model/->Mismatch (k expected) (k actual)))}
       res))))

(defspec map-matchers-mismatches-when-all-keys-have-a-mismatched-value
  {:max-size 10}
  (prop/for-all [expected (gen/such-that not-empty (gen/map gen/keyword gen/small-integer))
                 m        (gen/elements [matchers/equals matchers/embeds])]
    (let [actual (reduce-kv (fn [m k v] (assoc m k (inc v))) {} expected)
          res    (core/match (m expected) actual)]
      (standalone/match?
       {::result/type :mismatch
        ::result/value
        (reduce (fn [m [k]]
                  (assoc m k (model/->Mismatch (k expected) (k actual))))
                {}
                actual)}
       res))))

(defspec map-matchers-mismatch-when-expected-keys-are-missing
  {:max-size 10}
  (prop/for-all [expected (gen/such-that not-empty (gen/map gen/keyword gen/small-integer))
                 m        (gen/elements [matchers/equals matchers/embeds])]
    (let [k      (first (keys expected))
          actual (dissoc expected k)
          res    (core/match (m expected) actual)]
      (standalone/match?
       {::result/type :mismatch
        ::result/value (assoc actual k (model/->Missing (get expected k)))}
       res))))

(defspec map-matchers-mismatch-any-non-map-value
  {:max-size 10}
  (prop/for-all [m        (gen/elements [matchers/equals matchers/embeds])
                 expected (gen/map gen/keyword gen/any-equatable)
                 actual   (gen/such-that (comp not map?) gen/any-equatable)]
    (let [res (core/match (m expected) actual)]
      (standalone/match?
       {::result/type  :mismatch
        ::result/value (model/->Mismatch expected actual)}
       res))))

(defspec matcher-for-arities
  {:max-size 10}
  (prop/for-all [v gen/any]
    (= (core/-matcher-for v)
       (core/-matcher-for v {}))))

(deftest matcher-for-with-overrides
  (is (= matchers/embeds
         (core/-matcher-for {:this :map})))
  (is (= matchers/embeds
         (core/-matcher-for {:this :map} [])))
  (is (= matchers/equals
         (core/-matcher-for {:this :map} [map? matchers/equals])))
  (testing "legacy API using type instead of predicate"
    (is (= matchers/equals (core/-matcher-for {:this :map}
                                              {clojure.lang.IPersistentMap matchers/equals})))))

(deftest false-check-for-sets
  (testing "gracefully handle matching `false` values"
    (is (= (core/match false false)
           {::result/type   :match
            ::result/value  false
            ::result/weight 0}))
    (is (= (core/match (matchers/in-any-order [false]) [false])
           {::result/type   :match
            ::result/value  [false]
            ::result/weight 0}))
    (is (= (core/match #{false} #{false})
           {::result/type   :match
            ::result/value  #{false}
            ::result/weight 0}))))

(deftest test-indicates-match?
  (is (core/indicates-match? {::result/type :match
                              ::result/weight 0
                              ::result/value :does-not-matter}))

  (is (not (core/indicates-match? {::result/type :mismatch
                                   ::result/weight 1
                                   ::result/value :does-not-matter}))))

(deftest test-deprectated-match?
  (is (core/indicates-match? {::result/type :match
                              ::result/weight 0
                              ::result/value :does-not-matter}))

  (is (not (core/indicates-match? {::result/type :mismatch
                                   ::result/weight 1
                                   ::result/value :does-not-matter}))))

(defspec sequence-matchers-match-when-elements-match-in-order
  {:max-size 5}
  (prop/for-all [v (gen/one-of [(gen/vector gen/any-equatable)
                                (gen/list   gen/any-equatable)])
                 m (gen/elements [matchers/equals matchers/in-any-order])]
    (core/indicates-match?
     (core/match (m v) v))))

(defspec sequence-matchers-mismatch-missing-elements
  {:max-size 5}
  (prop/for-all [v (gen/one-of [(gen/vector gen/any-equatable)
                                (gen/list   gen/any-equatable)])
                 m (gen/elements [matchers/equals matchers/in-any-order])]
    (not
     (core/indicates-match?
      (core/match (m (concat v [:extra])) v)))))

(defspec sequence-matchers-mismatch-extra-elements
  {:max-size 5}
  (prop/for-all [v (gen/one-of [(gen/vector gen/any-equatable)
                                (gen/list   gen/any-equatable)])
                 m (gen/elements [matchers/equals matchers/in-any-order])]
    (not
     (core/indicates-match?
      (core/match (m v) (concat v [:extra]))))))

(defspec sequence-matchers-mismatch-incorrect-element
  {:max-size 5}
  (prop/for-all [nums      (gen/vector gen/small-integer 1 5)
                 coll-type (gen/elements [vector list])
                 m         (gen/elements [matchers/equals matchers/in-any-order])]
    (let [expected (apply coll-type nums)
          actual   (apply coll-type (update nums 0 inc))]
      (not
       (core/indicates-match?
        (core/match (m expected) actual))))))

(defspec sequence-matchers-mismatch-non-sequences
  {:max-size 5}
  (prop/for-all [expected (gen/one-of [(gen/vector gen/any-equatable)
                                       (gen/list   gen/any-equatable)])
                 actual   gen/any-equatable
                 m (gen/elements [matchers/equals matchers/in-any-order])]
    (or (sequential? actual)
        (not
         (core/indicates-match?
          (core/match (m expected) actual))))))

(deftest sequence-matchers
  (testing "on the equals matcher for sequences"
    (testing "on element mismatches, marks each mismatch"
      (is (= {::result/type   :mismatch
              ::result/value  [(model/->Mismatch 1 2) (model/->Mismatch 2 1)]
              ::result/weight 2}
             (core/match (matchers/equals [(matchers/equals 1) (matchers/equals 2)]) [2 1])))

      (is (= {::result/type   :mismatch
              ::result/value  [1 (model/->Mismatch 2 3)]
              ::result/weight 1}
             (core/match (matchers/equals [(matchers/equals 1) (matchers/equals 2)]) [1 3]))))

    (testing "mismatch reports elements in correct order"
      (is (= {::result/type   :mismatch
              ::result/value  [1 2 (model/->Mismatch 3 4)]
              ::result/weight 1}
             (core/match (matchers/equals [(matchers/equals 1) (matchers/equals 2) (matchers/equals 3)])
                         (list 1 2 4)))))

    (testing "when there are more elements than expected matchers, mark each extra element as Unexpected"
      (is (= {::result/type   :mismatch
              ::result/value  [1 2 (model/->Unexpected 3)]
              ::result/weight 1}
             (core/match (matchers/equals [(matchers/equals 1) (matchers/equals 2)]) [1 2 3]))))

    (testing "Mismatch plays well with nil"
      (is (= {::result/type   :mismatch
              ::result/value  [1 2 (model/->Mismatch 3 nil)]
              ::result/weight 1}
             (core/match (matchers/equals [(matchers/equals 1) (matchers/equals 2) (matchers/equals 3)]) [1 2 nil]))))

    (testing "when there are more matchers then actual elements, append the expected values marked as Missing"
      (is (= {::result/type   :mismatch
              ::result/value  [1 2 (model/->Missing 3)]
              ::result/weight 1}
             (core/match (matchers/equals [(matchers/equals 1) (matchers/equals 2) (matchers/equals 3)]) [1 2])))

      (is (= {::result/type   :mismatch
              ::result/value  [{:a 1} (model/->Missing {:b (matchers/equals 2)})]
              ::result/weight 1}
             (core/match (matchers/equals [(matchers/equals {:a (matchers/equals 1)}) (matchers/equals {:b (matchers/equals 2)})]) [{:a 1}])))))

  (testing "on the in-any-order sequence matcher"
    (is (= {::result/type   :match
            ::result/value  [{:id 2 :x 2} {:id 1 :x 1}]
            ::result/weight 0}
           (core/match
             (matchers/in-any-order [(matchers/equals {:id (matchers/equals 1) :x (matchers/equals 1)})
                                     (matchers/equals {:id (matchers/equals 2) :x (matchers/equals 2)})])
             [{:id 2 :x 2} {:id 1 :x 1}])))
    (is (= {::result/type   :match
            ::result/value  [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]
            ::result/weight 0}
           (core/match
             (matchers/in-any-order [(matchers/equals {:id (matchers/equals 1) :x (matchers/equals 1)})
                                     (matchers/equals {:id (matchers/equals 2) :x (matchers/equals 2)})
                                     (matchers/equals {:id (matchers/equals 3) :x (matchers/equals 3)})])
             [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]))))

  (testing "the 1-argument arity has a simple all-or-nothing behavior:"
    (testing "in-any-order for list of same value/matchers"
      (is (= {::result/type   :match
              ::result/value  [2 2]
              ::result/weight 0}
             (core/match (matchers/in-any-order [(matchers/equals 2) (matchers/equals 2)]) [2 2]))))

    (testing "when there the matcher and list count differ, mark specific mismatches"
      (is (match? {::result/type   :mismatch
                   ::result/value  (matchers/in-any-order [1 2 (model/->Unexpected 3)])
                   ::result/weight 1}
                  (core/match (matchers/in-any-order [(matchers/equals 1) (matchers/equals 2)]) [1 2 3])))

      (is (match? {::result/type   :mismatch
                   ::result/value  (matchers/in-any-order [2 1 (model/->Missing 3)])
                   ::result/weight 1}
                  (core/match (matchers/in-any-order [(matchers/equals 1) (matchers/equals 2) (matchers/equals 3)]) [1 2]))))))

(deftest nesting-multiple-matchers
  (testing "on nesting equals matchers for sequences"
    (is (= {::result/type   :match
            ::result/value  [[1 2] 20]
            ::result/weight 0}
           (core/match
             (matchers/equals [(matchers/equals [(matchers/equals 1) (matchers/equals 2)]) (matchers/equals 20)])
             [[1 2] 20])))

    (is (= {::result/type   :mismatch
            ::result/value  [[1 (model/->Mismatch 2 5)] 20]
            ::result/weight 1}
           (core/match
             (matchers/equals [(matchers/equals [(matchers/equals 1) (matchers/equals 2)]) (matchers/equals 20)])
             [[1 5] 20])))

    (is (= {::result/type   :mismatch
            ::result/value  [[1 (model/->Mismatch 2 5)] (model/->Mismatch 20 21)]
            ::result/weight 2}
           (core/match
             (matchers/equals [(matchers/equals [(matchers/equals 1) (matchers/equals 2)]) (matchers/equals 20)])
             [[1 5] 21]))))

  (testing "sequence type is preserved in mismatch output"
    (is (true? (instance? (class (vector)) (-> (matchers/equals [(matchers/equals [(matchers/equals 1)])])
                                               (core/match [[2]])
                                               ::result/value))))

    (is (true? (instance? (class (list 'placeholder)) (-> (matchers/equals [(matchers/equals [(matchers/equals 1)])])
                                                          (core/match (list [2]))
                                                          ::result/value)))))

  (testing "nesting in-any-order matchers"
    (is (= {::result/type   :match
            ::result/value  [{:id 1 :a 1} {:id 2 :a 2}]
            ::result/weight 0}
           (core/match
             (matchers/in-any-order [(matchers/equals {:id (matchers/equals 1) :a (matchers/equals 1)})
                                     (matchers/equals {:id (matchers/equals 2) :a (matchers/equals 2)})])
             [{:id 1 :a 1} {:id 2 :a 2}]))))

  (testing "nesting matchers/embeds  for maps"
    (is (= {::result/type   :match
            ::result/value  {:a 42 :m {:x "foo"}}
            ::result/weight 0}
           (core/match
             (matchers/embeds  {:a (matchers/equals 42) :m (matchers/embeds  {:x (matchers/equals "foo")})})
             {:a 42 :m {:x "foo"}})))

    (is (= {::result/type   :mismatch
            ::result/value  {:a 42
                             :m {:x (model/->Mismatch "foo" "bar")}}
            ::result/weight 1}
           (core/match (matchers/embeds  {:a (matchers/equals 42)
                                          :m (matchers/embeds  {:x (matchers/equals "foo")})})
                       {:a 42
                        :m {:x "bar"}})))

    (is (= {::result/type   :mismatch
            ::result/value  {:a (model/->Mismatch 42 43)
                             :m {:x (model/->Mismatch "foo" "bar")}}
            ::result/weight 2}
           (core/match (matchers/embeds  {:a (matchers/equals 42)
                                          :m (matchers/embeds  {:x (matchers/equals "foo")})})
                       {:a 43
                        :m {:x "bar"}}))))

  (is (= {::result/type   :match
          ::result/value  [{:a 42 :b 1337} 20]
          ::result/weight 0}
         (core/match (matchers/equals [(matchers/equals {:a (matchers/equals 42)
                                                         :b (matchers/equals 1337)})
                                       (matchers/equals 20)])
                     [{:a 42 :b 1337} 20])))

  (is (= {::result/type   :mismatch
          ::result/value  [{:a (model/->Mismatch 42 43) :b 1337} 20]
          ::result/weight 1}
         (core/match (matchers/equals [(matchers/equals {:a (matchers/equals 42)
                                                         :b (matchers/equals 1337)})
                                       (matchers/equals 20)])
                     [{:a 43 :b 1337} 20]))))

;; Since the parser namespace needs to be loaded to interpret functions as
;; matchers, and we don't want to load the parser namespace, we need to manually
;; wrap functions in a predicate matcher
(defn- pred-matcher [expected]
  (assert ifn? expected)
  (core/->PredMatcher expected (str expected)))

(deftest predicate-matcher
  (is (= {::result/type   :match
          ::result/value  [1 2]
          ::result/weight 0}
         (core/match (matchers/equals [(pred-matcher odd?) (pred-matcher even?)]) [1 2])))

  (is (match? {::result/type   :mismatch
               ::result/value  [1 any?]
               ::result/weight 1}
              (core/match (matchers/equals [(pred-matcher odd?) (pred-matcher even?)]) [1]))) (let [matchers [(pred-matcher odd?) (pred-matcher even?)]]
                                                                                                (testing "no matching when there are more matchers than elements"
                                                                                                  (is (match? (matchers/embeds {:matched?  false
                                                                                                                                :unmatched [any? any?]
                                                                                                                                :matched   empty?})
                                                                                                              (#'core/matches-in-any-order? matchers [] true [])))
                                                                                                  (is (match? (matchers/embeds {:matched?  false
                                                                                                                                :unmatched [any?]
                                                                                                                                :matched   [any?]})
                                                                                                              (#'core/matches-in-any-order? matchers [1] false [])))
                                                                                                  (is (match? (matchers/embeds {:matched?  false
                                                                                                                                :unmatched [any?]
                                                                                                                                :matched   [any?]})
                                                                                                              (#'core/matches-in-any-order? matchers [1] true []))))

                                                                                                (testing "subset will recur on matchers"
                                                                                                  (is (match? (matchers/embeds {:matched?  true
                                                                                                                                :unmatched nil?
                                                                                                                                :matched   [any? any?]})
                                                                                                              (#'core/matches-in-any-order? matchers [5 4 1 2] true [])))
                                                                                                  (is (match? (matchers/embeds {:matched?  true
                                                                                                                                :unmatched nil?
                                                                                                                                :matched   [any? any?]})
                                                                                                              (#'core/matches-in-any-order? matchers [5 1 3 2] true []))))

                                                                                                (testing "works well with identical matchers"
                                                                                                  (is (match? (matchers/embeds {:matched?  true
                                                                                                                                :unmatched empty?
                                                                                                                                :matched   [any? any?]})
                                                                                                              (#'core/matches-in-any-order? [(matchers/equals 2) (matchers/equals 2)] [2 2] false []))))

                                                                                                (testing "mismatch if there are more matchers than actual elements"
                                                                                                  (is (match? {::result/type  :mismatch
                                                                                                               ::result/value (matchers/in-any-order [(model/->Missing any?) 5])
                                                                                                               ::result/weight 1}
                                                                                                              (#'core/match-any-order matchers [5] false)))
                                                                                                  (is (match? {::result/type   :mismatch
                                                                                                               ::result/value  (matchers/in-any-order [5 (model/->Missing any?)])
                                                                                                               ::result/weight 1}
                                                                                                              (#'core/match-any-order matchers [5] true))))))

(deftest matching-for-absence-in-map
  (is (= {::result/type   :match
          ::result/value  {:a 42}
          ::result/weight 0}
         (core/match (matchers/equals {:a (matchers/equals 42)
                                       :b matchers/absent})
                     {:a 42})))

  (is (match? {::result/type   :mismatch
               ::result/value  {:a 42
                                :b {:actual 43}}
               ::result/weight #(or (= 1 %) (= 2 %))}
              (core/match (matchers/embeds {:a (matchers/equals 42)
                                            :b matchers/absent})
                          {:a 42
                           :b 43}))))

(deftest absent-interaction-with-keys-pointing-to-nil-values
  (is (match? {::result/type   :mismatch
               ::result/value  {:a 42
                                :b {:actual nil}}
               ::result/weight 2}
              (core/match (matchers/equals {:a (matchers/equals 42)
                                            :b matchers/absent})
                          {:a 42
                           :b nil}))))

(deftest using-absent-incorrectly-outside-of-a-map
  (is (match? {::result/type   :mismatch
               ::result/value  [42 {:message "`absent` matcher should only be used as the value in a map"}]
               ::result/weight 1}
              (core/match (matchers/equals [(matchers/equals 42) matchers/absent])
                          [42]))))

(deftest let-us-think-of-a-name-later
  (are [?matcher]
       ;; expression - must return a truthy value to pass 
       (match? {::result/type   :mismatch
                ::result/value  {:expected-type-msg
                                 #(str/starts-with? % (-> ?matcher var meta :name str))
                                 :provided
                                 "provided: 1"}
                ::result/weight number?}
               (core/match (?matcher 1) 1))
    ;; values to be bound, one at a time, to ?matcher
    matchers/prefix
    matchers/embeds))

(def pred-set #{(pred-matcher odd?) (pred-matcher pos?)})
(def pred-seq [(pred-matcher odd?) (pred-matcher pos?)])

(def short-equals-seq (map matchers/equals [1 3]))

(deftest embeds-test
  (testing "embeds for sequences"
    (is (= {::result/type   :match
            ::result/value  [3 4 1]
            ::result/weight 0}
           (core/match (matchers/embeds  short-equals-seq) [3 4 1])))
    (is (= {::result/type   :match
            ::result/value  [3 4 1 5]
            ::result/weight 0}
           (core/match (matchers/embeds  short-equals-seq) [3 4 1 5]))))

  (testing "embeds /set-equals matches"
    (is (= {::result/type   :match
            ::result/value  #{1 3}
            ::result/weight 0}
           (core/match (matchers/embeds  pred-set) #{1 3})))
    (is (= {::result/type   :match
            ::result/value  #{1 3}
            ::result/weight 0}
           (core/match (matchers/set-embeds pred-seq) #{1 3})))
    (is (= {::result/type   :match
            ::result/value  #{1 3}
            ::result/weight 0}
           (core/match (matchers/equals pred-set) #{1 3})))
    (is (= {::result/type   :match
            ::result/value  #{1 3}
            ::result/weight 0}
           (core/match (matchers/set-equals pred-seq) #{1 3}))))

  (testing "embeds /equals mismatches due to type"
    (is (match? {::result/type   :mismatch
                 ::result/value  {:actual   #{1 3}
                                  :expected any?}
                 ::result/weight 1}
                (core/match (matchers/equals pred-seq) #{1 3})))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:actual   [1 3]
                                  :expected any?}
                 ::result/weight 1}
                (core/match (matchers/equals pred-set) [1 3])))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:actual   #{1 3}
                                  :expected any?}
                 ::result/weight 1}
                (core/match (matchers/embeds pred-seq) #{1 3})))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:actual   [1 3]
                                  :expected any?}
                 ::result/weight 1}
                (core/match (matchers/embeds pred-set) [1 3])))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:expected-type-msg #"^embeds *"
                                  :provided          #"^provided: 1"}
                 ::result/weight 1}
                (core/match (matchers/embeds 1) [1]))))

  (testing "embeds /set-equals mismatches due to type"
    (is (match? {::result/type   :mismatch
                 ::result/value  {:actual   [1 3]
                                  :expected any?}
                 ::result/weight 1}
                (core/match (matchers/set-embeds pred-seq) [1 3])))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:actual   [1 3]
                                  :expected any?}
                 ::result/weight 1}
                (core/match (matchers/set-equals pred-seq) [1 3])))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:expected-type-msg #"^set-embeds*"
                                  :provided          #"^provided: 1"}
                 ::result/weight 1}
                (core/match (matchers/set-embeds 1) [1 3])))
    (is (match? {::result/type   :mismatch
                 ::result/value  {:expected-type-msg #"^set-equals*"
                                  :provided          #"^provided: 1"}
                 ::result/weight 1}
                (core/match (matchers/set-equals 1) [1 3]))))

  (testing "embeds /set-equals mismatches due to content"
    (is (match? {::result/type   :mismatch
                 ::result/value  #{1 {:actual   -2
                                      :expected any?}}
                 ::result/weight 1}
                (core/match (matchers/set-embeds pred-set) #{1 -2})))

    (is (match? {::result/type   :mismatch
                 ::result/weight 1
                 ::result/value  #{1 {:actual   -2
                                      :expected any?}}}
                (core/match (matchers/set-embeds pred-seq) #{1 -2})))

    (is (match? {::result/type   :mismatch
                 ::result/weight 1
                 ::result/value  #{1 {:actual   -2
                                      :expected any?}}}
                (core/match (matchers/equals pred-set) #{1 -2})))

    (is (match? {::result/type   :mismatch
                 ::result/weight 1
                 ::result/value  #{1 {:actual   -2
                                      :expected any?}}}
                (core/match (matchers/set-equals pred-seq) #{1 -2})))))

(def even-odd-set #{(pred-matcher #(and (odd? %) (pos? %)))
                    (pred-matcher even?)})
(def even-odd-seq (into [] even-odd-set))

(deftest even-odd-test
  (testing "Order agnostic checks show fine-grained mismatch details"
    (is (= {::result/type   :mismatch
            ::result/value  #{1 2 (model/->Unexpected -3)}
            ::result/weight 1}
           (core/match (matchers/equals even-odd-set) #{1 2 -3})))

    (is (match? {::result/type   :mismatch
                 ::result/value  (matchers/in-any-order [1 2 (model/->Unexpected -3)])
                 ::result/weight 1}
                (core/match (matchers/in-any-order even-odd-seq) [1 2 -3])))

    (is (match? {::result/type   :mismatch
                 ::result/value  (matchers/in-any-order [1 (model/->Missing any?)])
                 ::result/weight 1}
                (core/match (matchers/in-any-order even-odd-seq) [1])))
    (is (match? {::result/type   :mismatch
                 ::result/value  #{1 (model/->Missing any?)}
                 ::result/weight 1}
                (core/match (matchers/equals even-odd-set) #{1})))))

(deftest in-any-order-minimal-mismatch-test
  (is (= {::result/type   :mismatch
          ::result/value  [{:a "1" :x (model/->Mismatch "12" "12=")}]
          ::result/weight 1}
         (core/match (matchers/equals [(matchers/equals {:a (matchers/equals "1") :x (matchers/equals "12")})])
                     [{:a "1" :x "12="}])))

  (is (match? {::result/type   :mismatch
               ::result/value  (matchers/in-any-order [{:a "1" :x (model/->Mismatch "12" "12=")}
                                                       {:a "2" :x (model/->Mismatch "14" "14=")}])
               ::result/weight 2}
              (core/match (matchers/in-any-order [(matchers/equals {:a (matchers/equals "2") :x (matchers/equals "14")})
                                                  (matchers/equals {:a (matchers/equals "1") :x (matchers/equals "12")})])
                          [{:a "1" :x "12="} {:a "2" :x "14="}])))

  (is (match? {::result/type   :mismatch
               ::result/value  (matchers/in-any-order [{:a "2" :x (model/->Mismatch "14" "14=")}
                                                       {:a "1" :x (model/->Mismatch "12" "12=")}])
               ::result/weight 2}
              (core/match (matchers/in-any-order [(matchers/equals {:a (matchers/equals "2") :x (matchers/equals "14")})
                                                  (matchers/equals {:a (matchers/equals "1") :x (matchers/equals "12")})])
                          [{:a "1" :x "12="} {:a "2" :x "14="}]))))
