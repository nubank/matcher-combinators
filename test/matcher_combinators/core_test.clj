(ns matcher-combinators.core-test
  (:require [midje.sweet :refer :all :exclude [exactly contains] :as sweet]
            [clojure.string :as str]
            [matcher-combinators.core :as core :refer :all]
            [matcher-combinators.matchers :refer :all]
            [matcher-combinators.model :as model]))

(facts "on the leaf values matcher: v"
  (match (equals 42) 42) => [:match 42]
  (match (equals 42) 43) => [:mismatch (model/->Mismatch 42 43)])

(fact "on map matchers"
  (tabular
    (facts "on common behaviors among all map matchers"
      (fact "matches when given a map with matching values for every key"
        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 42, :b 1337})
        => [:match {:a 42, :b 1337}])

      (fact "when actual values fail to match expected matchers for
            corresponding keys, mismatch marking each value Mismatch"
        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 43, :b 1337})
        => [:mismatch {:a (model/->Mismatch 42 43), :b 1337}]

        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 42, :b 13373})
        => [:mismatch {:a 42, :b (model/->Mismatch 1337 13373)}]

        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 43, :b 13373})
        => [:mismatch {:a (model/->Mismatch 42 43), :b (model/->Mismatch 1337 13373)}])

      (fact "when actual input map doesn't contain values for expected keys,
            mismatch marking each key with a Missing value"
        (match (?map-matcher {:a (equals 42)}) {})
        => [:mismatch {:a (model/->Missing 42)}]

        (match (?map-matcher {:a (equals 42) :b (equals 42)}) {:b 42})
        => [:mismatch {:b 42, :a (model/->Missing 42)}])

      (tabular
        (fact "mismatch when given an actual input that is not a map"
          (match (?map-matcher {:a (equals 1)}) ?actual)
          => [:mismatch (model/->Mismatch {:a (equals 1)} ?actual)])
        ?actual
        1
        "a1"
        [[:a 1]]))
    ?map-matcher
    embeds
    equals)

  (facts "on the equals matcher for maps"
    (fact "when the actual input map contains keys for which there are no
          corresponding matchers specified, mismatch marking each key with an
          Unexpected value"
      (match (equals {:a (equals 42)}) {:a 42 :b 1337})
      => [:mismatch {:a 42, :b (model/->Unexpected 1337)}]

      (match (equals {:a (equals 42)}) {:b 42})
      => [:mismatch {:b (model/->Unexpected 42), :a (model/->Missing 42)}])))

(facts "on sequence matchers"
  (tabular
    (facts "on common behaviors among all sequence matchers"
      (fact "matches when actual sequence elements match each matcher, in order and in total"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})])
               [{:id 1, :a 1} {:id 2, :a 2}])
        => [:match [{:id 1, :a 1} {:id 2, :a 2}]])

      (fact "mismatch when none of the expected matchers is a match for one
             element of the given sequence"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})])
               [{:id 1 :a 1} {:id 2 :a 200}])
        => (just [:mismatch anything]))

      (fact "only matches when all expected matchers are matched by elements of
             the given sequence"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})
                                   (equals {:id (equals 3) :a (equals 3)})])
               [{:id 1 :a 1} {:id 2 :a 2}])
        => (just [:mismatch anything]))

      (fact "only matches when all of the input sequence elements are matched
             by an expected matcher"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})])
               [{:id 1 :a 1} {:id 2 :a 2} {:id 3 :a 3}])
        => (just [:mismatch anything]))

      (tabular
        (fact "mismatches when the actual input is not a sequence"
          (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                     (equals {:id (equals 2) :a (equals 2)})]) ?actual)
          => [:mismatch (model/->Mismatch [(equals {:id (equals 1) :a (equals 1)})
                                           (equals {:id (equals 2) :a (equals 2)})] ?actual)])
        ?actual
        12
        "12"
        '12
        :12
        {:x 12}
        #{1 2}))

    ?sequence-matcher
    equals
    in-any-order)

  (facts "on the equals matcher for sequences"
    (fact "on element mismatches, marks each mismatch"
      (match (equals [(equals 1) (equals 2)]) [2 1])
      => [:mismatch [(model/->Mismatch 1 2) (model/->Mismatch 2 1)]]

      (match (equals [(equals 1) (equals 2)]) [1 3])
      => [:mismatch [1 (model/->Mismatch 2 3)]])

    (fact "when there are more elements than expected matchers, mark each extra element as Unexpected"
      (match (equals [(equals 1) (equals 2)]) [1 2 3])
      => [:mismatch [1 2 (model/->Unexpected 3)]])

    (fact "Mismatch plays well with nil"
      (match (equals [(equals 1) (equals 2) (equals 3)]) [1 2 nil])
      => [:mismatch [1 2 (model/->Mismatch 3 nil)]])

    (fact "when there are more matchers then actual elements, append the expected values marked as Missing"
      (match (equals [(equals 1) (equals 2) (equals 3)]) [1 2])
      => [:mismatch [1 2 (model/->Missing 3)]]))

  (facts "on the in-any-order sequence matcher"
    (tabular
      (facts "common behavior for all in-any-order arities"
        (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
          (match
            (?in-any-order-matcher [(equals {:id (equals 1) :x (equals 1)})
                                    (equals {:id (equals 2) :x (equals 2)})])
            [{:id 2 :x 2} {:id 1 :x 1}])
          => [:match [{:id 2 :x 2} {:id 1 :x 1}]]

          (match
            (?in-any-order-matcher [(equals {:id (equals 1) :x (equals 1)})
                                    (equals {:id (equals 2) :x (equals 2)})
                                    (equals {:id (equals 3) :x (equals 3)})])
            [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}])
          => [:match [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]]))
      ?in-any-order-matcher
      in-any-order)

    (facts "the 1-argument arity has a simple all-or-nothing behavior:"
      (fact "in-any-order for list of same value/matchers"
        (match (in-any-order [(equals 2) (equals 2)]) [2 2])
        => [:match [2 2]])

      (fact "when there the matcher and list count differ, mark specific mismatches"
        (match (in-any-order [(equals 1) (equals 2)]) [1 2 3])
        => (just [:mismatch (just [1 2 (model/->Unexpected 3)]
                                  :in-any-order)])

        (match (in-any-order [(equals 1) (equals 2) (equals 3)]) [1 2])
        => (just [:mismatch (just [1 2 (model/->Missing 3)]
                                  :in-any-order)])))))

(facts "on nesting multiple matchers"
  (facts "on nesting equals matchers for sequences"
    (match
      (equals [(equals [(equals 1) (equals 2)]) (equals 20)])
      [[1 2] 20])
    => [:match [[1 2] 20]]

    (match
      (equals [(equals [(equals 1) (equals 2)]) (equals 20)])
      [[1 5] 20])
    => [:mismatch [[1 (model/->Mismatch 2 5)] 20]]

    (match
      (equals [(equals [(equals 1) (equals 2)]) (equals 20)])
      [[1 5] 21])
    => [:mismatch [[1 (model/->Mismatch 2 5)] (model/->Mismatch 20 21)]])

  (fact "sequence type is preserved in mismatch output"
    (-> (equals [(equals [(equals 1)])])
        (match [[2]])
        second)
    => #(instance? (class (vector)) %)

    (-> (equals [(equals [(equals 1)])])
        (match (list [2]))
        second)
    => #(instance? (class (list 'placeholder)) %))

  (fact "nesting in-any-order matchers"
    (match
      (in-any-order [(equals {:id (equals 1) :a (equals 1)})
                     (equals {:id (equals 2) :a (equals 2)})])
      [{:id 1 :a 1} {:id 2 :a 2}])
    => [:match [{:id 1 :a 1} {:id 2 :a 2}]])

  (facts "nesting embeds for maps"
    (match
      (embeds {:a (equals 42) :m (embeds {:x (equals "foo")})})
      {:a 42 :m {:x "foo"}})
    => [:match {:a 42 :m {:x "foo"}}]


    (match (embeds {:a (equals 42)
                    :m (embeds {:x (equals "foo")})})
           {:a 42
            :m {:x "bar"}})
    => [:mismatch {:a 42
                   :m {:x (model/->Mismatch "foo" "bar")}}]

    (match (embeds {:a (equals 42)
                    :m (embeds {:x (equals "foo")})})
           {:a 43
            :m {:x "bar"}})
    => [:mismatch {:a (model/->Mismatch 42 43)
                   :m {:x (model/->Mismatch "foo" "bar")}}])

  (match (equals [(equals {:a (equals 42)
                           :b (equals 1337)})
                  (equals 20)])
         [{:a 42 :b 1337} 20])
  => [:match [{:a 42 :b 1337} 20]]

  (match (equals [(equals {:a (equals 42)
                           :b (equals 1337)})
                  (equals 20)])
         [{:a 43 :b 1337} 20])
  => [:mismatch [{:a (model/->Mismatch 42 43) :b 1337} 20]])


;; Since the parser namespace needs to be loaded to interpret functions as
;; matchers, and we don't want to load the parser namespce, we need to manually
;; wrap functions in a predicate matcher
(defrecord PredMatcher [expected]
  core/Matcher
  (match [this actual]
    (core/match-pred expected actual)))

(defn- pred-matcher [expected]
  (assert ifn? expected)
  (->PredMatcher expected))

(fact
  (match (equals [(pred-matcher odd?) (pred-matcher even?)]) [1 2])
  => [:match [1 2]]
  (match (equals [(pred-matcher odd?) (pred-matcher even?)]) [1])
  => (just [:mismatch (just [1 anything])]))

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
                        :unmatched empty?
                        :matched   vector?})
    (#'core/matches-in-any-order? matchers [5 1 3 2] true [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything])
                        :matched   (just [anything])}))
  (fact "works well with identical matchers"
    (#'core/matches-in-any-order? [(equals 2) (equals 2)] [2 2] false [])
    => (sweet/contains {:matched?  true
                        :unmatched empty?
                        :matched   (just [anything anything])}))
  (fact "mismatch if there are more matchers than actual elements"
    (#'core/match-any-order matchers [5] false)
    => (just [:mismatch (just [(just (model/->Missing anything)) 5]
                              :in-any-order)])
    (#'core/match-any-order matchers [5] true)
    => [:mismatch (model/->Mismatch matchers [5])]))

(tabular
  (fact "Providing seq/map matcher with incorrect input leads to automatic mismatch"
    (core/match (?matcher 1) 1)
    => (just [:mismatch
              (sweet/contains {:expected-type-msg
                               #(str/starts-with? % (-> ?matcher var meta :name str))

                               :provided
                               "provided: 1"})]))
  ?matcher
  prefix
  embeds)

(def pred-set #{(pred-matcher odd?) (pred-matcher pos?)})
(def pred-seq [(pred-matcher odd?) (pred-matcher pos?)])

(fact "embeds /set-equals matches"
  (core/match (embeds pred-set) #{1 3}) => (just [:match (just #{1 3})])
  (core/match (set-embeds pred-seq) #{1 3}) => (just [:match (just #{1 3})])
  (core/match (equals pred-set) #{1 3}) => (just [:match (just #{1 3})])
  (core/match (set-equals pred-seq) #{1 3}) => (just [:match (just #{1 3})]))

(fact "embeds /equals mismatches due to type"
  (core/match (equals pred-seq) #{1 3})
  => (just [:mismatch (just {:actual   #{1 3}
                             :expected anything})])
  (core/match (equals pred-set) [1 3])
  => (just [:mismatch (just {:actual   [1 3]
                             :expected anything})])
  (core/match (embeds pred-seq) #{1 3})
  => (just [:mismatch (just {:actual   #{1 3}
                             :expected anything})])
  (core/match (embeds pred-set) [1 3])
  => (just [:mismatch (just {:actual   [1 3]
                             :expected anything})])
  (core/match (embeds 1) [1])
  => (just [:mismatch (just {:expected-type-msg #"^embeds *"
                             :provided          #"^provided: 1"})]))

(fact "embeds /set-equals mismatches due to type"
  (core/match (set-embeds pred-seq) [1 3])
  => (just [:mismatch (just {:actual   [1 3]
                             :expected anything})])
  (core/match (set-equals pred-seq) [1 3])
  => (just [:mismatch (just {:actual   [1 3]
                             :expected anything})])
  (core/match (set-embeds 1) [1 3])
  => (just [:mismatch (just {:expected-type-msg #"^set-embeds*"
                             :provided          #"^provided: 1"})])
  (core/match (set-equals 1) [1 3])
  => (just [:mismatch (just {:expected-type-msg #"^set-equals*"
                             :provided          #"^provided: 1"})]))

(fact "embeds /set-equals mismatches due to content"
  (core/match (set-embeds pred-set) #{1 -2})
  => (just [:mismatch (just #{1 (just {:actual -2
                                       :form   anything})})])

  (core/match (set-embeds pred-seq) #{1 -2})
  => (just [:mismatch (just #{1 (just {:actual -2
                                       :form   anything})})])

  (core/match (equals pred-set) #{1 -2})
  => (just [:mismatch (just #{1 (just {:actual -2
                                       :form   anything})})])

  (core/match (set-equals pred-seq) #{1 -2})
  => (just [:mismatch (just #{1 (just {:actual -2
                                       :form   anything})})]))
