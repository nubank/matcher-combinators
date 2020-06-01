(ns matcher-combinators.core-test
  (:require [midje.sweet :refer :all :exclude [exactly contains] :as sweet]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.string :as str]
            [orchestra.spec.test :as spec.test]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.model :as model]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [matcher-combinators.specs] ;; to load specs
            [matcher-combinators.test-helpers :as test-helpers :refer [gen-any-equatable]]))

(use-fixtures :once test-helpers/instrument)

(defspec equals-matcher-matches-when-values-are-equal
  {:max-size 10}
  (prop/for-all [v gen-any-equatable]
                (core/match? (m/equals v) v)))

(defspec equals-matcher-mismatches-when-scalar-values-are-not-equal
  {:max-size 10}
  (prop/for-all [[a b] (gen/such-that (fn [[a b]] (not= a b))
                                      (gen/tuple gen/simple-type-equatable
                                                 gen/simple-type-equatable))]
                (core/match?
                 {::result/value (model/->Mismatch a b)}
                 (core/match (m/equals a) b))))

(defspec map-matchers-mismatches-when-one-key-has-a-mismatched-value
  {:max-size 10}
  (prop/for-all [matcher  (gen/elements [m/equals m/embeds])
                 expected (gen/such-that not-empty (gen/map gen/keyword gen/small-integer))]
                (let [k      (first (keys expected))
                      actual (update expected k inc)
                      res    (core/match (matcher expected) actual)]
                  (core/match?
                   {::result/type  :mismatch
                    ::result/value (assoc actual k (model/->Mismatch (k expected) (k actual)))}
                   res))))

(defspec map-matchers-mismatches-when-all-keys-have-a-mismatched-value
  {:max-size 10}
  (prop/for-all [matcher  (gen/elements [m/equals m/embeds])
                 expected (gen/such-that not-empty (gen/map gen/keyword gen/small-integer))]
                (let [actual (reduce-kv (fn [m k v] (assoc m k (inc v))) {} expected)
                      res    (core/match (matcher expected) actual)]
                  (core/match?
                   {::result/type :mismatch
                    ::result/value
                    (reduce (fn [m [k]]
                              (assoc m k (model/->Mismatch (k expected) (k actual))))
                            {}
                            actual)}
                   res))))

(defspec map-matchers-mismatch-when-expected-keys-are-missing
  {:max-size 10}
  (prop/for-all [matcher  (gen/elements [m/equals m/embeds])
                 expected (gen/such-that not-empty (gen/map gen/keyword gen/small-integer))]
                (let [k      (first (keys expected))
                      actual (dissoc expected k)
                      res    (core/match (matcher expected) actual)]
                  (core/match?
                   {::result/type :mismatch
                    ::result/value (assoc actual k (model/->Missing (get expected k)))}
                   res))))

(defspec map-matchers-mismatch-any-non-map-value
  {:max-size 10}
  (prop/for-all [matcher  (gen/elements [m/equals m/embeds])
                 expected (gen/map gen/keyword gen-any-equatable)
                 actual   (gen/such-that (comp not map?) gen-any-equatable)]
                (let [res (core/match (matcher expected) actual)]
                  (core/match?
                   {::result/type  :mismatch
                    ::result/value (model/->Mismatch expected actual)}
                   res))))

(defspec sequence-matchers-match-when-sequences-are-equal
  {:max-size 10}
  (prop/for-all [s (gen/such-that sequential? gen-any-equatable)
                 m (gen/elements [m/equals m/in-any-order])]
                (core/match? (m s) s)))

(defspec sequence-matchers-mismatch-when-no-element-matches-any-one-matcher
  {:max-size 10}
  (prop/for-all [s (gen/vector gen/nat 1 4)
                 e (gen/elements ['() []])
                 m (gen/elements [m/equals m/in-any-order])]
                (let [actual   (into e s)
                      expected (into [neg?] (drop 1 s))]
                  (not (core/match? (m expected) actual)))))

(defspec sequence-matchers-mismatch-when-there-is-extra-input
  {:max-size 10}
  (prop/for-all [s (gen/vector gen/nat 1 4)
                 e (gen/elements ['() []])
                 m (gen/elements [m/equals m/in-any-order])]
                (let [actual   (into e s)
                      expected (butlast s)]
                  (not (core/match? (m expected) actual)))))

(defspec sequence-matchers-mismatch-when-there-is-missing-input
  {:max-size 10}
  (prop/for-all [s (gen/vector gen/nat 1 4)
                 e (gen/elements ['() []])
                 m (gen/elements [m/equals m/in-any-order])]
                (let [actual   (into e (butlast s))
                      expected s]
                  (not (core/match? (m expected) actual)))))

(defspec sequence-matchers-match-when-sequences-are-equal
  {:max-size 10}
  (prop/for-all [expected (gen/such-that sequential? gen-any-equatable)
                 actual   gen/simple-type-equatable
                 m        (gen/elements [m/equals m/in-any-order])]
                (not (core/match? (m expected) actual))))

(facts "on sequence matchers"
  (facts "on the equals matcher for sequences"
    (fact "on element mismatches, marks each mismatch"
      (core/match (m/equals [(m/equals 1) (m/equals 2)]) [2 1])
      => {::result/type   :mismatch
          ::result/value  [(model/->Mismatch 1 2) (model/->Mismatch 2 1)]
          ::result/weight 2}

      (core/match (m/equals [(m/equals 1) (m/equals 2)]) [1 3])
      => {::result/type   :mismatch
          ::result/value  [1 (model/->Mismatch 2 3)]
          ::result/weight 1})

    (fact "mismatch reports elements in correct order"
      (core/match (m/equals [(m/equals 1) (m/equals 2) (m/equals 3)])
        (list 1 2 4))
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Mismatch 3 4)]
          ::result/weight 1})

    (fact "when there are more elements than expected matchers, mark each extra element as Unexpected"
      (core/match (m/equals [(m/equals 1) (m/equals 2)]) [1 2 3])
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Unexpected 3)]
          ::result/weight 1})

    (fact "Mismatch plays well with nil"
      (core/match (m/equals [(m/equals 1) (m/equals 2) (m/equals 3)]) [1 2 nil])
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Mismatch 3 nil)]
          ::result/weight 1})

    (fact "when there are more matchers then actual elements, append the expected values marked as Missing"
      (core/match (m/equals [(m/equals 1) (m/equals 2) (m/equals 3)]) [1 2])
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Missing 3)]
          ::result/weight 1}

      (core/match (m/equals [(m/equals {:a (m/equals 1)}) (m/equals {:b (m/equals 2)})]) [{:a 1}])
      => {::result/type   :mismatch
          ::result/value  [{:a 1} (model/->Missing {:b (m/equals 2)})]
          ::result/weight 1}))

  (facts "on the in-any-order sequence matcher"
    (tabular
      (facts "common behavior for all in-any-order arities"
        (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
          (core/match
           (?in-any-order-matcher [(m/equals {:id (m/equals 1) :x (m/equals 1)})
                                   (m/equals {:id (m/equals 2) :x (m/equals 2)})])
            [{:id 2 :x 2} {:id 1 :x 1}])
          => {::result/type   :match
              ::result/value  [{:id 2 :x 2} {:id 1 :x 1}]
              ::result/weight 0}

          (core/match
           (?in-any-order-matcher [(m/equals {:id (m/equals 1) :x (m/equals 1)})
                                   (m/equals {:id (m/equals 2) :x (m/equals 2)})
                                   (m/equals {:id (m/equals 3) :x (m/equals 3)})])
            [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}])
          => {::result/type   :match
              ::result/value  [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]
              ::result/weight 0}))
      ?in-any-order-matcher
      m/in-any-order)

    (facts "the 1-argument arity has a simple all-or-nothing behavior:"
      (fact "in-any-order for list of same value/matchers"
        (core/match (m/in-any-order [(m/equals 2) (m/equals 2)]) [2 2])
        => {::result/type   :match
            ::result/value  [2 2]
            ::result/weight 0})

      (fact "when there the matcher and list count differ, mark specific mismatches"
        (core/match (m/in-any-order [(m/equals 1) (m/equals 2)]) [1 2 3])
        => (just {::result/type   :mismatch
                  ::result/value  (just [1 2 (model/->Unexpected 3)]
                                        :in-any-order)
                  ::result/weight 1})

        (core/match (m/in-any-order [(m/equals 1) (m/equals 2) (m/equals 3)]) [1 2])
        => (just {::result/type   :mismatch
                  ::result/value  (just [1 2 (model/->Missing 3)]
                                        :in-any-order)
                  ::result/weight 1})))))

(spec.test/instrument)

(facts "on nesting multiple matchers"
  (facts "on nesting equals matchers for sequences"
    (core/match
     (m/equals [(m/equals [(m/equals 1) (m/equals 2)]) (m/equals 20)])
      [[1 2] 20])
    => {::result/type   :match
        ::result/value  [[1 2] 20]
        ::result/weight 0}

    (core/match
     (m/equals [(m/equals [(m/equals 1) (m/equals 2)]) (m/equals 20)])
      [[1 5] 20])
    => {::result/type   :mismatch
        ::result/value  [[1 (model/->Mismatch 2 5)] 20]
        ::result/weight 1}

    (core/match
     (m/equals [(m/equals [(m/equals 1) (m/equals 2)]) (m/equals 20)])
      [[1 5] 21])
    => {::result/type   :mismatch
        ::result/value  [[1 (model/->Mismatch 2 5)] (model/->Mismatch 20 21)]
        ::result/weight 2})

  (fact "sequence type is preserved in mismatch output"
    (-> (m/equals [(m/equals [(m/equals 1)])])
        (core/match [[2]])
        ::result/value)
    => #(instance? (class (vector)) %)

    (-> (m/equals [(m/equals [(m/equals 1)])])
        (core/match (list [2]))
        ::result/value)
    => #(instance? (class (list 'placeholder)) %))

  (fact "nesting in-any-order matchers"
    (core/match
     (m/in-any-order [(m/equals {:id (m/equals 1) :a (m/equals 1)})
                      (m/equals {:id (m/equals 2) :a (m/equals 2)})])
      [{:id 1 :a 1} {:id 2 :a 2}])
    => {::result/type   :match
        ::result/value  [{:id 1 :a 1} {:id 2 :a 2}]
        ::result/weight 0})

  (facts "nesting embeds for maps"
    (core/match
     (m/embeds {:a (m/equals 42) :m (m/embeds {:x (m/equals "foo")})})
      {:a 42 :m {:x "foo"}})
    => {::result/type   :match
        ::result/value  {:a 42 :m {:x "foo"}}
        ::result/weight 0} (core/match (m/embeds {:a (m/equals 42)
                                                  :m (m/embeds {:x (m/equals "foo")})})
                             {:a 42
                              :m {:x "bar"}})
    => {::result/type   :mismatch
        ::result/value  {:a 42
                         :m {:x (model/->Mismatch "foo" "bar")}}
        ::result/weight 1}

    (core/match (m/embeds {:a (m/equals 42)
                           :m (m/embeds {:x (m/equals "foo")})})
      {:a 43
       :m {:x "bar"}})
    => {::result/type   :mismatch
        ::result/value  {:a (model/->Mismatch 42 43)
                         :m {:x (model/->Mismatch "foo" "bar")}}
        ::result/weight 2})

  (core/match (m/equals [(m/equals {:a (m/equals 42)
                                    :b (m/equals 1337)})
                         (m/equals 20)])
    [{:a 42 :b 1337} 20])
  => {::result/type   :match
      ::result/value  [{:a 42 :b 1337} 20]
      ::result/weight 0}

  (core/match (m/equals [(m/equals {:a (m/equals 42)
                                    :b (m/equals 1337)})
                         (m/equals 20)])
    [{:a 43 :b 1337} 20])
  => {::result/type   :mismatch
      ::result/value  [{:a (model/->Mismatch 42 43) :b 1337} 20]
      ::result/weight 1})

;; Since the parser namespace needs to be loaded to interpret functions as
;; matchers, and we don't want to load the parser namespce, we need to manually
;; wrap functions in a predicate matcher
(defn- pred-matcher [expected]
  (assert ifn? expected)
  (core/->PredMatcher expected (str expected)))

(fact
 (core/match (m/equals [(pred-matcher odd?) (pred-matcher even?)]) [1 2])
  => {::result/type   :match
      ::result/value  [1 2]
      ::result/weight 0}
  (core/match (m/equals [(pred-matcher odd?) (pred-matcher even?)]) [1])
  => (just {::result/type   :mismatch
            ::result/value  (just [1 anything])
            ::result/weight 1}))

(let [matchers [(pred-matcher odd?) (pred-matcher even?)]]
  (fact "no matching when there are more matchers than elements"
    (#'core/matches-in-any-order? matchers [] true [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything anything])
                        :matched   empty?})
    (#'core/matches-in-any-order? matchers [1] false [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything])
                        :matched   (just [anything])})
    (#'core/matches-in-any-order? matchers [1] true [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything])
                        :matched   (just [anything])}))
  (fact "subset will recur on matchers"
    (#'core/matches-in-any-order? matchers [5 4 1 2] true [])
    => (sweet/contains {:matched?  true
                        :unmatched nil?
                        :matched   (just [anything anything])})
    (#'core/matches-in-any-order? matchers [5 1 3 2] true [])
    => (sweet/contains {:matched?  true
                        :unmatched nil?
                        :matched   (just [anything anything])}))
  (fact "works well with identical matchers"
    (#'core/matches-in-any-order? [(m/equals 2) (m/equals 2)] [2 2] false [])
    => (sweet/contains {:matched?  true
                        :unmatched empty?
                        :matched   (just [anything anything])}))
  (fact "mismatch if there are more matchers than actual elements"
    (#'core/match-any-order matchers [5] false)
    => (just {::result/type   :mismatch
              ::result/value  (just [(just (model/->Missing anything)) 5]
                                    :in-any-order)
              ::result/weight 1})
    (#'core/match-any-order matchers [5] true)
    => (just {::result/type   :mismatch
              ::result/value  (just [5 (just (model/->Missing anything))]
                                    :in-any-order)
              ::result/weight 1})))

(tabular
  (fact "matching for absence in map"
    (core/match (?matcher {:a (m/equals 42)
                           :b m/absent})
      {:a 42})
    => (just {::result/type   :match
              ::result/value  {:a 42}
              ::result/weight 0})

    (core/match (?matcher {:a (m/equals 42)
                           :b m/absent})
      {:a 42
       :b 43})
    => (just {::result/type   :mismatch
              ::result/value  (just {:a 42
                                     :b (just {:actual 43})})
              ::result/weight #(or (= 1 %) (= 2 %))}))
  ?matcher
  m/equals
  m/embeds)

(fact "`absent` interaction with keys pointing to `nil` values"
  (core/match (m/equals {:a (m/equals 42)
                         :b m/absent})
    {:a 42
     :b nil})
  => (just {::result/type   :mismatch
            ::result/value  (just {:a 42
                                   :b {:actual nil}})
            ::result/weight 2}))

(fact "using `absent` incorrectly outside of a map"
  (core/match (m/equals [(m/equals 42) m/absent])
    [42])
  => (just {::result/type   :mismatch
            ::result/value  (just [42 {:message "`absent` matcher should only be used as the value in a map"}])
            ::result/weight 1}))

(tabular
  (fact "Providing seq/map matcher with incorrect input leads to automatic mismatch"
    (core/match (?matcher 1) 1)
    => (just {::result/type   :mismatch
              ::result/value  (sweet/contains {:expected-type-msg
                                               #(str/starts-with? % (-> ?matcher var meta :name str))
                                               :provided
                                               "provided: 1"})
              ::result/weight number?}))
  ?matcher
  m/prefix
  m/embeds)

(def pred-set #{(pred-matcher odd?) (pred-matcher pos?)})
(def pred-seq [(pred-matcher odd?) (pred-matcher pos?)])

(def short-equals-seq (map m/equals [1 3]))

(fact "embeds for sequences"
  (core/match (m/embeds short-equals-seq) [3 4 1]) => (just {::result/type   :match
                                                             ::result/value  (just [3 4 1])
                                                             ::result/weight 0})
  (core/match (m/embeds short-equals-seq) [3 4 1 5]) => (just {::result/type   :match
                                                               ::result/value  (just [3 4 1 5])
                                                               ::result/weight 0}))

(fact "embeds /set-equals matches"
  (core/match (m/embeds pred-set) #{1 3}) => (just {::result/type   :match
                                                    ::result/value  (just #{1 3})
                                                    ::result/weight 0})
  (core/match (m/set-embeds pred-seq) #{1 3}) => (just {::result/type   :match
                                                        ::result/value  (just #{1 3})
                                                        ::result/weight 0})
  (core/match (m/equals pred-set) #{1 3}) => (just {::result/type   :match
                                                    ::result/value  (just #{1 3})
                                                    ::result/weight 0})
  (core/match (m/set-equals pred-seq) #{1 3}) => (just {::result/type   :match
                                                        ::result/value  (just #{1 3})
                                                        ::result/weight 0}))

(fact "embeds /equals mismatches due to type"
  (core/match (m/equals pred-seq) #{1 3})
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   #{1 3}
                                   :expected anything})
            ::result/weight 1})
  (core/match (m/equals pred-set) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (m/embeds pred-seq) #{1 3})
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   #{1 3}
                                   :expected anything})
            ::result/weight 1})
  (core/match (m/embeds pred-set) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (m/embeds 1) [1])
  => (just {::result/type   :mismatch
            ::result/value  (just {:expected-type-msg #"^embeds *"
                                   :provided          #"^provided: 1"})
            ::result/weight 1}))

(fact "embeds /set-equals mismatches due to type"
  (core/match (m/set-embeds pred-seq) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (m/set-equals pred-seq) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (m/set-embeds 1) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:expected-type-msg #"^set-embeds*"
                                   :provided          #"^provided: 1"})
            ::result/weight 1})
  (core/match (m/set-equals 1) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:expected-type-msg #"^set-equals*"
                                   :provided          #"^provided: 1"})
            ::result/weight 1}))

(fact "embeds /set-equals mismatches due to content"
  (core/match (m/set-embeds pred-set) #{1 -2})
  => (just {::result/type  :mismatch
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})
            ::result/weight 1})

  (core/match (m/set-embeds pred-seq) #{1 -2})
  => (just {::result/type :mismatch
            ::result/weight 1
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})})

  (core/match (m/equals pred-set) #{1 -2})
  => (just {::result/type :mismatch
            ::result/weight 1
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})})

  (core/match (m/set-equals pred-seq) #{1 -2})
  => (just {::result/type :mismatch
            ::result/weight 1
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})}))

(def even-odd-set #{(pred-matcher #(and (odd? %) (pos? %)))
                    (pred-matcher even?)})
(def even-odd-seq (into [] even-odd-set))
(fact "Order agnostic checks show fine-grained mismatch details"
  (core/match (m/equals even-odd-set) #{1 2 -3})
  => (just {::result/type   :mismatch
            ::result/value  #{1 2 (model/->Unexpected -3)}
            ::result/weight 1})

  (core/match (m/in-any-order even-odd-seq) [1 2 -3])
  => (just {::result/type   :mismatch
            ::result/value  (just [1 2 (model/->Unexpected -3)]
                                  :in-any-order)
            ::result/weight 1})

  (core/match (m/in-any-order even-odd-seq) [1])
  => (just {::result/type   :mismatch
            ::result/value  (just [1 (just (model/->Missing anything))]
                                  :in-any-order)
            ::result/weight 1})

  (core/match (m/equals even-odd-set) #{1})
  => (just {::result/type   :mismatch
            ::result/value  (just #{1 (just (model/->Missing anything))}
                                  :in-any-order)
            ::result/weight 1}))

(fact "in-any-order minimal mismatch test"
  (core/match (m/equals [(m/equals {:a (m/equals "1") :x (m/equals "12")})])
    [{:a "1" :x "12="}])
  => {::result/type   :mismatch
      ::result/value  [{:a "1" :x (model/->Mismatch "12" "12=")}]
      ::result/weight 1}

  (core/match (m/in-any-order [(m/equals {:a (m/equals "2") :x (m/equals "14")})
                               (m/equals {:a (m/equals "1") :x (m/equals "12")})])
    [{:a "1" :x "12="} {:a "2" :x "14="}])
  => (just {::result/type   :mismatch
            ::result/value  (just [{:a "1" :x (model/->Mismatch "12" "12=")}
                                   {:a "2" :x (model/->Mismatch "14" "14=")}]
                                  :in-any-order)
            ::result/weight 2})

  (core/match (m/in-any-order [(m/equals {:a (m/equals "2") :x (m/equals "14")})
                               (m/equals {:a (m/equals "1") :x (m/equals "12")})])
    [{:a "1" :x "12="} {:a "2" :x "14="}])
  => (just {::result/type   :mismatch
            ::result/value  (just [{:a "2" :x (model/->Mismatch "14" "14=")}
                                   {:a "1" :x (model/->Mismatch "12" "12=")}]
                                  :in-any-order)
            ::result/weight 2}))

(spec.test/unstrument)
