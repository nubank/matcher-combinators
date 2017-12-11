(ns matcher-combinators.core-test
  (:require [midje.sweet :refer :all :exclude [exactly]]
            [matcher-combinators.core :refer :all]
            [matcher-combinators.model :as model]))

(facts "on the leaf values matcher: v"
  (match (equals-value 42) 42) => [:match 42]
  (match (equals-value 42) 43) => [:mismatch (model/->Mismatch 42 43)]
  (fact "value missing"
    (match (equals-value 42) nil) => [:mismatch (model/->Missing 42)]))

(fact "on map matchers"
  (tabular
    (facts "on common behaviors among all map matchers"
      (fact "matches when given a map with matching values for every key"
        (match (?map-matcher {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 1337})
        => [:match {:a 42, :b 1337}])

      (fact "when actual values fail to match expected matchers for
            corresponding keys, mismatch marking each value Mismatch"
        (match (?map-matcher {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 1337})
        => [:mismatch {:a (model/->Mismatch 42 43), :b 1337}]

        (match (?map-matcher {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 13373})
        => [:mismatch {:a 42, :b (model/->Mismatch 1337 13373)}]

        (match (?map-matcher {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 13373})
        => [:mismatch {:a (model/->Mismatch 42 43), :b (model/->Mismatch 1337 13373)}])

      (fact "when actual input map doesn't contain values for expected keys,
            mismatch marking each key with a Missing value"
        (match (?map-matcher {:a (equals-value 42)}) {})
        => [:mismatch {:a (model/->Missing 42)}]

        (match (?map-matcher {:a (equals-value 42) :b (equals-value 42)}) {:b 42})
        => [:mismatch {:b 42, :a (model/->Missing 42)}])

      (tabular
        (fact "mismatch when given an actual input that is not a map"
          (match (?map-matcher {:a (equals-value 1)}) ?actual)
          => [:mismatch (model/->Mismatch {:a (equals-value 1)} ?actual)])
        ?actual
        1
        "a1"
        [[:a 1]]))
    ?map-matcher
    contains-map
    equals-map)

  (facts "on the equals-map matcher"
    (fact "when the actual input map contains keys for which there are no
          corresponding matchers specified, mismatch marking each key with an
          Unexpected value"
      (match (equals-map {:a (equals-value 42)}) {:a 42 :b 1337})
      => [:mismatch {:a 42, :b (model/->Unexpected 1337)}]

      (match (equals-map {:a (equals-value 42)}) {:b 42})
      => [:mismatch {:b (model/->Unexpected 42), :a (model/->Missing 42)}])))

(def in-any-order-selecting (partial in-any-order :id))

(facts "on sequence matchers"
  (tabular
    (facts "on common behaviors among all sequence matchers"
      (fact "matches when actual sequence elements match each matcher, in order and in total"
        (match (?sequence-matcher [(equals-map {:id (equals-value 1), :a (equals-value 1)})
                                   (equals-map {:id (equals-value 2), :a (equals-value 2)})])
               [{:id 1, :a 1} {:id 2, :a 2}])
        => [:match [{:id 1, :a 1} {:id 2, :a 2}]])

      (fact "mismatch when none of the expected matchers is a match for one element of the given sequence"
        (match (?sequence-matcher [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                                   (equals-map {:id (equals-value 2) :a (equals-value 2)})])
               [{:id 1 :a 1} {:id 2 :a 200}])
        => (just [:mismatch anything]))

      (fact "only matches when all expected matchers are matched by elements of the given sequence"
        (match (?sequence-matcher [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                                   (equals-map {:id (equals-value 2) :a (equals-value 2)})
                                   (equals-map {:id (equals-value 3) :a (equals-value 3)})])
               [{:id 1 :a 1} {:id 2 :a 2}])
        => (just [:mismatch anything]))

      (fact "only matches when all of the input sequence elements are matched by an expected matcher"
        (match (?sequence-matcher [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                                   (equals-map {:id (equals-value 2) :a (equals-value 2)})])
               [{:id 1 :a 1} {:id 2 :a 2} {:id 3 :a 3}])
        => (just [:mismatch anything]))

      (tabular
        (fact "mismatches when the actual input is not a sequence"
          (match (?sequence-matcher [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                                     (equals-map {:id (equals-value 2) :a (equals-value 2)})]) ?actual)
          => [:mismatch (model/->Mismatch [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                                           (equals-map {:id (equals-value 2) :a (equals-value 2)})] ?actual)])
        ?actual
        12
        "12"
        '12
        :12
        {:x 12}
        #{1 2}))

    ?sequence-matcher
    equals-seq
    in-any-order
    in-any-order-selecting)

  (facts "on the equals-seq matcher"
    (fact "on element mismatches, marks each mismatch"
      (match (equals-seq [(equals-value 1) (equals-value 2)]) [2 1])
      => [:mismatch [(model/->Mismatch 1 2) (model/->Mismatch 2 1)]]

      (match (equals-seq [(equals-value 1) (equals-value 2)]) [1 3])
      => [:mismatch [1 (model/->Mismatch 2 3)]])

    (fact "when there are more elements than expected matchers, mark each extra element as Unexpected"
      (match (equals-seq [(equals-value 1) (equals-value 2)]) [1 2 3])
      => [:mismatch [1 2 (model/->Unexpected 3)]])

    (fact "when there are more matchers then actual elements, append the expected values marked as Missing"
      (match (equals-seq [(equals-value 1) (equals-value 2) (equals-value 3)]) [1 2])
      => [:mismatch [1 2 (model/->Missing 3)]]))

  (facts "on the in-any-order sequence matcher"
    (tabular
      (facts "common behavior for all in-any-order arities"
        (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
          (match
            (?in-any-order-matcher [(equals-map {:id (equals-value 1) :x (equals-value 1)})
                                    (equals-map {:id (equals-value 2) :x (equals-value 2)})])
            [{:id 2 :x 2} {:id 1 :x 1}])
          => [:match [{:id 2 :x 2} {:id 1 :x 1}]]

          (match
            (?in-any-order-matcher [(equals-map {:id (equals-value 1) :x (equals-value 1)})
                                    (equals-map {:id (equals-value 2) :x (equals-value 2)})
                                    (equals-map {:id (equals-value 3) :x (equals-value 3)})])
            [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}])
          => [:match [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]]))
      ?in-any-order-matcher
      in-any-order
      in-any-order-selecting)

    (facts "the 1-argument arity has a simple all-or-nothing behavior:"
      (facts "in case of element mismatches, marks the whole sequence as a mismatch"
        (match (in-any-order [(equals-value 1) (equals-value 2)]) [1 2 3])
        => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] [1 2 3])])

      (facts "when the given sequence contains elements not matched by any
             matcher, marks the whole sequence as a mismatch"
        (match (in-any-order [(equals-value 1) (equals-value 2)]) [1 2 3])
        => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] [1 2 3])])

      (fact "when there are matchers not matched by any input elements, marks
            the whole sequence as a mismatch"
        (match (in-any-order [(equals-value 1) (equals-value 2)]) [1])
        => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] [1])]))

    (facts "the 2-argument arity will look for an element to match by id"
      (fact "when the given sequence contains elements not matched by their
            selected matcher, marks them as Mismatches"
        (match (in-any-order :id [(equals-map {:id (equals-value 2) :a (equals-value 2)})
                                  (equals-map {:id (equals-value 1) :a (equals-value 1)})])
               [{:id 1 :a 1} {:id 2 :a 200}])
        => [:mismatch [{:id 1 :a 1} {:id 2 :a (model/->Mismatch 2 200)}]]

        (match
          (in-any-order :id [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                             (equals-map {:id (equals-value 2) :a (equals-value 2)})])
          [{:id 1 :a 100} {:id 2 :a 200}])
        => [:mismatch [{:id 1 :a (model/->Mismatch 1 100)} {:id 2 :a (model/->Mismatch 2 200)}]])

      (fact "when there are matchers not matched by any input elements, append
            their values as Missing elements"
        (match (in-any-order :id [(equals-value 1) (equals-value 2) (equals-value 3)]) [1 2])
        => [:mismatch [1 2 (model/->Missing 3)]]))))

(facts "on nesting multiple matchers"
  (facts "on nesting equals-seq matchers"
    (match
      (equals-seq [(equals-seq [(equals-value 1) (equals-value 2)]) (equals-value 20)])
      [[1 2] 20])
    => [:match [[1 2] 20]]

    (match
      (equals-seq [(equals-seq [(equals-value 1) (equals-value 2)]) (equals-value 20)])
      [[1 5] 20])
    => [:mismatch [[1 (model/->Mismatch 2 5)] 20]]

    (match
      (equals-seq [(equals-seq [(equals-value 1) (equals-value 2)]) (equals-value 20)])
      [[1 5] 21])
    => [:mismatch [[1 (model/->Mismatch 2 5)] (model/->Mismatch 20 21)]])

  (fact "nesting in-any-order matchers"
    (match
      (in-any-order [(equals-map {:id (equals-value 1) :a (equals-value 1)})
                     (equals-map {:id (equals-value 2) :a (equals-value 2)})])
      [{:id 1 :a 1} {:id 2 :a 2}])
    => [:match [{:id 1 :a 1} {:id 2 :a 2}]])

  (facts "nesting contains-map"
    (match
      (contains-map {:a (equals-value 42) :m (contains-map {:x (equals-value "foo")})})
      {:a 42 :m {:x "foo"}})
    => [:match {:a 42 :m {:x "foo"}}]


    (match (contains-map {:a (equals-value 42)
                          :m (contains-map {:x (equals-value "foo")})})
           {:a 42
            :m {:x "bar"}})
    => [:mismatch {:a 42
                   :m {:x (model/->Mismatch "foo" "bar")}}]

    (match (contains-map {:a (equals-value 42)
                          :m (contains-map {:x (equals-value "foo")})})
           {:a 43
            :m {:x "bar"}})
    => [:mismatch {:a (model/->Mismatch 42 43)
                   :m {:x (model/->Mismatch "foo" "bar")}}])

  (match (equals-seq [(equals-map {:a (equals-value 42)
                                        :b (equals-value 1337)})
                           (equals-value 20)])
         [{:a 42 :b 1337} 20])
  => [:match [{:a 42 :b 1337} 20]]

  (match (equals-seq [(equals-map {:a (equals-value 42)
                                        :b (equals-value 1337)})
                           (equals-value 20)])
         [{:a 43 :b 1337} 20])
  => [:mismatch [{:a (model/->Mismatch 42 43) :b 1337} 20]])


(facts "on selecting matchers for a value"
  (select? (equals-value 10) :id 10) => truthy
  (select? (equals-value 10) :id 11) => falsey

  (select? (equals-value 10) :whatever 10) => truthy
  (select? (equals-value 10) :whatever 11) => falsey

  (select? (equals-map {:id (equals-value 10)}) :id {:id 10}) => truthy
  (select? (equals-map {:id (equals-value 10)}) :id {:id 20}) => falsey
  (select? (equals-map {:id (equals-value 10)}) :xx {:id 10}) => falsey

  (select? (equals-map {:id (equals-value 10) :a (equals-value 42)}) :id {:id 10 :a 42})   => truthy
  (select? (equals-map {:id (equals-value 10) :a (equals-value 42)}) :id {:id 20 :a 42})   => falsey
  (select? (equals-map {:id (equals-value 10) :a (equals-value 42)}) :xx {:id 10 :a 42})   => falsey
  (select? (equals-map {:id (equals-value 10) :a (equals-value 42)}) :id {:id 10 :a 1337}) => truthy)


(future-fact "on contains-elements sequence matcher")
