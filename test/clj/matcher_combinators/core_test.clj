(ns matcher-combinators.core-test
  (:require [midje.sweet :as sweet]
            [clojure.test :refer [are deftest testing is use-fixtures]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.string :as str]
            [orchestra.spec.test :as spec.test]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model]
            [matcher-combinators.result :as result]
            [matcher-combinators.parser]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.test-helpers :as test-helpers])
  (:import (clojure.lang Associative)))

(use-fixtures :once test-helpers/instrument)

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

;(defspec sequence-matchers-mismatch-non-sequences
;  {:max-size 5}
;  (prop/for-all [expected (gen/one-of [(gen/vector gen/any-equatable)
;                                       (gen/list   gen/any-equatable)])
;                 actual   (gen/such-that (complement sequential?) gen/any-equatable)
;                 m (gen/elements [matchers/equals matchers/in-any-order])]
;                (not
;                 (core/indicates-match?
;                  (core/match (m expected) actual)))))

(spec.test/instrument)

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
    ;(tabular
    ;  (facts "common behavior for all in-any-order arities"
    ;    (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
    ;      (match
    ;       (?in-any-order-matcher [(matchers/equals {:id (matchers/equals 1) :x (matchers/equals 1)})
    ;                               (matchers/equals {:id (matchers/equals 2) :x (matchers/equals 2)})])
    ;        [{:id 2 :x 2} {:id 1 :x 1}])
    ;      => {::result/type   :match
    ;          ::result/value  [{:id 2 :x 2} {:id 1 :x 1}]
    ;          ::result/weight 0}
    ;
    ;      (match
    ;       (?in-any-order-matcher [(matchers/equals {:id (matchers/equals 1) :x (matchers/equals 1)})
    ;                               (matchers/equals {:id (matchers/equals 2) :x (matchers/equals 2)})
    ;                               (matchers/equals {:id (matchers/equals 3) :x (matchers/equals 3)})])
    ;        [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}])
    ;      => {::result/type   :match
    ;          ::result/value  [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]
    ;          ::result/weight 0}))
    ;  ?in-any-order-matcher
    ;  in-any-order)

    (testing "the 1-argument arity has a simple all-or-nothing behavior:"
      (testing "in-any-order for list of same value/matchers"
        (is (= {::result/type   :match
                ::result/value  [2 2]
                ::result/weight 0}
               (core/match (matchers/in-any-order [(matchers/equals 2) (matchers/equals 2)]) [2 2]))))
      
      (is (match? {::result/type   :mismatch
                   ::result/value  (matchers/in-any-order [2 1 (model/->Missing 3)])
                   ::result/weight 1}
                  (core/match (matchers/in-any-order [(matchers/equals 1) (matchers/equals 2) (matchers/equals 3)]) [1 2])))
      )))

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

  ;(core/match (matchers/equals [(pred-matcher odd?) (pred-matcher even?)]) [1])
  ;(just {::result/type   :mismatch
  ;          ::result/value  (just [1 anything])
  ;          ::result/weight 1})


  (let [matchers [(pred-matcher odd?) (pred-matcher even?)]]
    ;(testing "no matching when there are more matchers than elements"
    ;      (#'core/matches-in-any-order? matchers [] true [])
    ;      => (sweet/contains {:matched?  false
    ;                          :unmatched (just [anything anything])
    ;                          :matched   empty?})
    ;      (#'core/matches-in-any-order? matchers [1] false [])
    ;      => (sweet/contains {:matched?  false
    ;                          :unmatched (just [anything])
    ;                          :matched   (just [anything])})
    ;      (#'core/matches-in-any-order? matchers [1] true [])
    ;      => (sweet/contains {:matched?  false
    ;                          :unmatched (just [anything])
    ;                          :matched   (just [anything])}))

    ;(fact "subset will recur on matchers"
    ;      (#'core/matches-in-any-order? matchers [5 4 1 2] true [])
    ;      => (sweet/contains {:matched?  true
    ;                          :unmatched nil?
    ;                          :matched   (just [anything anything])})
    ;      (#'core/matches-in-any-order? matchers [5 1 3 2] true [])
    ;      => (sweet/contains {:matched?  true
    ;                          :unmatched nil?
    ;                          :matched   (just [anything anything])}))
    ;(fact "works well with identical matchers"
    ;      (#'core/matches-in-any-order? [(matchers/equals 2) (matchers/equals 2)] [2 2] false [])
    ;      => (sweet/contains {:matched?  true
    ;                          :unmatched empty?
    ;                          :matched   (just [anything anything])}))

    ;(testing "mismatch if there are more matchers than actual elements"
    ;      (#'core/match-any-order matchers [5] false)
    ;      => (just {::result/type   :mismatch
    ;                ::result/value  (just [(just (model/->Missing anything)) 5]
    ;                                      :in-any-order)
    ;                ::result/weight 1})
    ;      (#'core/match-any-order matchers [5] true)
    ;      => (just {::result/type   :mismatch
    ;                ::result/value  (just [5 (just (model/->Missing anything))]
    ;                                      :in-any-order)
    ;                ::result/weight 1}))
))

(deftest absent-in-map
  (testing "matching for absence in map"
    (is (= {::result/type   :match
            ::result/value  {:a 42}
            ::result/weight 0}
           (core/match {:a (matchers/equals 42)
                        :b matchers/absent }
                       {:a 42})))

    ;(core/match (?matcher {:a (matchers/equals 42)
    ;                       :b matchers/absent })
    ;            {:a 42
    ;             :b 43})
    ;=> (just {::result/type   :mismatch
    ;          ::result/value  (just {:a 42
    ;                                 :b (just {:actual 43})})
    ;          ::result/weight #(or (= 1 %) (= 2 %))})
    ))

;(fact "`absent` interaction with keys pointing to `nil` values"
;  (core/match (matchers/equals {:a (matchers/equals 42)
;                       :b matchers/absent })
;    {:a 42
;     :b nil})
;  => (just {::result/type   :mismatch
;            ::result/value  (just {:a 42
;                                   :b {:actual nil}})
;            ::result/weight 2}))
;
;(fact "using `absent` incorrectly outside of a map"
;  (core/match (matchers/equals [(matchers/equals 42) matchers/absent ])
;    [42])
;  => (just {::result/type   :mismatch
;            ::result/value  (just [42 {:message "`absent` matcher should only be used as the value in a map"}])
;            ::result/weight 1}))

;(tabular
;  (fact "Providing seq/map matcher with incorrect input leads to automatic mismatch"
;    (core/match (?matcher 1) 1)
;    => (just {::result/type   :mismatch
;              ::result/value  (sweet/contains {:expected-type-msg
;                                               #(str/starts-with? % (-> ?matcher var meta :name str))
;                                               :provided
;                                               "provided: 1"})
;              ::result/weight number?}))
;  ?matcher
;  prefix
;  embeds)

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

  ;(fact "embeds /equals mismatches due to type"
  ;      (core/match (matchers/equals pred-seq) #{1 3})
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:actual   #{1 3}
  ;                                       :expected anything})
  ;                ::result/weight 1})
  ;      (core/match (matchers/equals pred-set) [1 3])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:actual   [1 3]
  ;                                       :expected anything})
  ;                ::result/weight 1})
  ;      (core/match (matchers/embeds  pred-seq) #{1 3})
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:actual   #{1 3}
  ;                                       :expected anything})
  ;                ::result/weight 1})
  ;      (core/match (matchers/embeds  pred-set) [1 3])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:actual   [1 3]
  ;                                       :expected anything})
  ;                ::result/weight 1})
  ;      (core/match (matchers/embeds  1) [1])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:expected-type-msg #"^embeds *"
  ;                                       :provided          #"^provided: 1"})
  ;                ::result/weight 1}))

  ;(fact "embeds /set-equals mismatches due to type"
  ;      (core/match (matchers/set-embeds pred-seq) [1 3])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:actual   [1 3]
  ;                                       :expected anything})
  ;                ::result/weight 1})
  ;      (core/match (matchers/set-equals pred-seq) [1 3])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:actual   [1 3]
  ;                                       :expected anything})
  ;                ::result/weight 1})
  ;      (core/match (matchers/set-embeds 1) [1 3])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:expected-type-msg #"^set-embeds*"
  ;                                       :provided          #"^provided: 1"})
  ;                ::result/weight 1})
  ;      (core/match (matchers/set-equals 1) [1 3])
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just {:expected-type-msg #"^set-equals*"
  ;                                       :provided          #"^provided: 1"})
  ;                ::result/weight 1}))

  ;(fact "embeds /set-equals mismatches due to content"
  ;      (core/match (matchers/set-embeds pred-set) #{1 -2})
  ;      => (just {::result/type   :mismatch
  ;                ::result/value  (just #{1 (just {:actual   -2
  ;                                                 :expected anything})})
  ;                ::result/weight 1})
  ;
  ;      (core/match (matchers/set-embeds pred-seq) #{1 -2})
  ;      => (just {::result/type   :mismatch
  ;                ::result/weight 1
  ;                ::result/value  (just #{1 (just {:actual   -2
  ;                                                 :expected anything})})})
  ;
  ;      (core/match (matchers/equals pred-set) #{1 -2})
  ;      => (just {::result/type   :mismatch
  ;                ::result/weight 1
  ;                ::result/value  (just #{1 (just {:actual   -2
  ;                                                 :expected anything})})})
  ;
  ;      (core/match (matchers/set-equals pred-seq) #{1 -2})
  ;      => (just {::result/type   :mismatch
  ;                ::result/weight 1
  ;                ::result/value  (just #{1 (just {:actual   -2
  ;                                                 :expected anything})})}))
)

(def even-odd-set #{(pred-matcher #(and (odd? %) (pos? %)))
                    (pred-matcher even?)})
(def even-odd-seq (into [] even-odd-set))

(deftest even-odd-test
  (testing "Order agnostic checks show fine-grained mismatch details"
    (is (= {::result/type   :mismatch
            ::result/value  #{1 2 (model/->Unexpected -3)}
            ::result/weight 1}
           (core/match (matchers/equals even-odd-set) #{1 2 -3})))

    ;(core/match (matchers/in-any-order even-odd-seq) [1 2 -3])
    ;    => (just {::result/type   :mismatch
    ;              ::result/value  (just [1 2 (model/->Unexpected -3)]
    ;                                    :in-any-order)
    ;              ::result/weight 1})
    ;
    ;    (core/match (matchers/in-any-order even-odd-seq) [1])
    ;    => (just {::result/type   :mismatch
    ;              ::result/value  (just [1 (just (model/->Missing anything))]
    ;                                    :in-any-order)
    ;              ::result/weight 1})
    ;
    ;    (core/match (matchers/equals even-odd-set) #{1})
    ;    => (just {::result/type   :mismatch
    ;              ::result/value  (just #{1 (just (model/->Missing anything))}
    ;                                    :in-any-order)
    ;              ::result/weight 1})
))

(deftest in-any-order-minimal-mismatch-test
  (is (= {::result/type   :mismatch
          ::result/value  [{:a "1" :x (model/->Mismatch "12" "12=")}]
          ::result/weight 1}
         (core/match (matchers/equals [(matchers/equals {:a (matchers/equals "1") :x (matchers/equals "12")})])
           [{:a "1" :x "12="}])))

  ;(core/match (matchers/in-any-order [(matchers/equals {:a (matchers/equals "2") :x (matchers/equals "14")})
  ;                           (matchers/equals {:a (matchers/equals "1") :x (matchers/equals "12")})])
  ;  [{:a "1" :x "12="} {:a "2" :x "14="}])
  ;=> (just {::result/type   :mismatch
  ;          ::result/value  (just [{:a "1" :x (model/->Mismatch "12" "12=")}
  ;                                 {:a "2" :x (model/->Mismatch "14" "14=")}]
  ;                                :in-any-order)
  ;          ::result/weight 2})
  ;
  ;(core/match (matchers/in-any-order [(matchers/equals {:a (matchers/equals "2") :x (matchers/equals "14")})
  ;                           (matchers/equals {:a (matchers/equals "1") :x (matchers/equals "12")})])
  ;  [{:a "1" :x "12="} {:a "2" :x "14="}])
  ;=> (just {::result/type   :mismatch
  ;          ::result/value  (just [{:a "2" :x (model/->Mismatch "14" "14=")}
  ;                                 {:a "1" :x (model/->Mismatch "12" "12=")}]
  ;                                :in-any-order)
  ;          ::result/weight 2})
)

(spec.test/unstrument)
