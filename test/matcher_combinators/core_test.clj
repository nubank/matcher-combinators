(ns matcher-combinators.core-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.core :refer :all]
            [matcher-combinators.model :as model]))

(facts "on the leaf values matcher: v"
  (match (equals-value 42) 42) => [:match 42]
  (match (equals-value 42) 43) => [:mismatch (model/->Mismatch 42 43)]
  (fact "value missing"
    (match (equals-value 42) nil) => [:mismatch (model/->Missing 42)]))


(facts "on the contains-map matcher"
  (fact "perfect match"
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 1337}) => [:match {:a 42, :b 1337}])

  (fact "value mismatches"
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 1337}) => [:mismatch {:a (model/->Mismatch 42 43), :b 1337}]
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 13373}) => [:mismatch {:a 42, :b (model/->Mismatch 1337 13373)}]
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 13373}) => [:mismatch {:a (model/->Mismatch 42 43), :b (model/->Mismatch 1337 13373)}])


  (fact "missing expected keys"
    (match (contains-map {:a (equals-value 42)}) {:b 42}) => [:mismatch {:b 42, :a (model/->Missing 42)}]
    (match (contains-map {:a (equals-value 42) :b (equals-value 42)}) {:b 42}) => [:mismatch {:b 42, :a (model/->Missing 42)}])

  (facts "nesting contains-map"
    (match (contains-map {:a (equals-value 42) :m (contains-map {:x (equals-value "foo")})}) {:a 42 :m {:x "foo"}})
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
                   :m {:x (model/->Mismatch "foo" "bar")}}]

    (fact "when not given a map"
      (match (contains-map {:a (equals-value 10)}) 10) => [:mismatch (model/->Mismatch {:a (equals-value 10)} 10)])))

(facts "on the equals-map matcher"
  (fact "perfect match"
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 1337}) => [:match {:a 42, :b 1337}])

  (fact "value mismatches"
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 1337}) => [:mismatch {:a (model/->Mismatch 42 43), :b 1337}]
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 13373}) => [:mismatch {:a 42, :b (model/->Mismatch 1337 13373)}]
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 13373}) => [:mismatch {:a (model/->Mismatch 42 43), :b (model/->Mismatch 1337 13373)}])

  (fact "missing expected keys"
    (match (equals-map {:a (equals-value 42) :b (equals-value 42)}) {:b 42}) => [:mismatch {:b 42, :a (model/->Missing 42)}]


  (fact "observing extra keys"
    (match (equals-map {:a (equals-value 42)}) {:a 42 :b 1337})
    => [:mismatch {:a 42, :b (model/->Unexpected 1337)}]

    (match (equals-map {:a (equals-value 42)}) {:b 42})
    => [:mismatch {:b (model/->Unexpected 42), :a (model/->Missing 42)}]))

  (fact "when not given a map"
    (match (equals-map {:a (equals-value 10)}) 10) => [:mismatch (model/->Mismatch {:a (equals-value 10)} 10)]))

(facts "on the equals-sequence matcher"
  (fact "perfect match"
    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [1 2])
    => [:match [1 2]])

  (fact "element mismatches"
    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [2 1])
    => [:mismatch [(model/->Mismatch 1 2) (model/->Mismatch 2 1)]]

    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [1 3])
    => [:mismatch [1 (model/->Mismatch 2 3)]])

  (fact "actual more than expected"
    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [1 2 3])
    => [:mismatch [1 2 (model/->Unexpected 3)]])

  (facts "on nesting equals-sequence matchers"
    (match (equals-sequence [(equals-sequence [(equals-value 1) (equals-value 2)]) (equals-value 20)]) [[1 2] 20])
    => [:match [[1 2] 20]]

    (match (equals-sequence [(equals-sequence [(equals-value 1) (equals-value 2)]) (equals-value 20)]) [[1 5] 20])
    => [:mismatch [[1 (model/->Mismatch 2 5)] 20]]

    (match (equals-sequence [(equals-sequence [(equals-value 1) (equals-value 2)]) (equals-value 20)]) [[1 5] 21])
    => [:mismatch [[1 (model/->Mismatch 2 5)] (model/->Mismatch 20 21)]])

  (tabular
    (fact "mismatches when not given a sequence"
     (match (equals-sequence [(equals-value 1) (equals-value 2)]) ?actual)
     => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] ?actual)])
    ?actual
    12
    "12"
    '12
    :12
    {:x 12}
    #{1 2})

  (fact "mismatch when there are more matchers then actual elements"
    (match (equals-sequence [(equals-value 1) (equals-value 2) (equals-value 3)]) [1 2])
    => [:mismatch [1 2 (model/->Missing 3)]]))

(facts "on nesting multiple matchers"
  (match (equals-sequence [(equals-map {:a (equals-value 42), :b (equals-value 1337)}) (equals-value 20)])
         [{:a 42 :b 1337} 20])
  => [:match [{:a 42 :b 1337} 20]]

  (match (equals-sequence [(equals-map {:a (equals-value 42), :b (equals-value 1337)}) (equals-value 20)])
         [{:a 43 :b 1337} 20])
  => [:mismatch [{:a (model/->Mismatch 42 43) :b 1337} 20]])

(facts "on the in-any-order sequence matcher"
  (facts "the 1-argument arity has a simple all-or-nothing behavior:"
    (fact "matches a sequence with elements corresponding to the expected matchers, in order"
      (match (in-any-order [(equals-value 1) (equals-value 2)]) [1 2])
      => [:match [1 2]])

    (fact "does not match when none of the expected matchers is a match for one element of the given sequence"
      (match (in-any-order [(equals-value 1) (equals-value 2)]) [1 2000])
      => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] [1 2000])])

    (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
      (match (in-any-order [(equals-value 1) (equals-value 2)]) [2 1]) => [:match [2 1]]
      (match (in-any-order [(equals-value 1) (equals-value 2) (equals-value 3)]) [2 1 3]) => [:match [2 1 3]])

    (fact "only matches when all expected matchers are matched by elements of the given sequence"
      (match (in-any-order [(equals-value 1) (equals-value 2)]) [1])
      => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] [1])])

    (facts "does not match when the given sequence contains elements not matched by any matcher"
      (match (in-any-order [(equals-value 1) (equals-value 2)]) [1 2 3])
      => [:mismatch (model/->Mismatch [(equals-value 1) (equals-value 2)] [1 2 3])])

    (fact "nesting matchers"
      (match (in-any-order [(equals-map {:id (equals-value 1) :a (equals-value 1)}) (equals-map {:id (equals-value 2) :a (equals-value 2)})])
             [{:id 1 :a 1} {:id 2 :a 2}])
      => [:match [{:id 1 :a 1} {:id 2 :a 2}]]))

  (facts "the 2-argument arity will look for an element to match by id"
    (fact "matches a sequence with elements corresponding to the expected matchers, in order"
      (match (in-any-order :id [(equals-map {:id (equals-value 1) :a (equals-value 1)}) (equals-map {:id (equals-value 2) :a (equals-value 2)})])
             [{:id 1 :a 1} {:id 2 :a 2}])
      => [:match [{:id 1 :a 1} {:id 2 :a 2}]])

    (fact "does not match when none of the expected matchers is a match for one element of the given sequence"
      (match (in-any-order :id [(equals-map {:id (equals-value 1) :a (equals-value 1)}) (equals-map {:id (equals-value 2) :a (equals-value 2)})])
             [{:id 1 :a 1} {:id 2 :a 200}])
      => [:mismatch [{:id 1 :a 1} {:id 2 :a (model/->Mismatch 2 200)}]])

    (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
      (match (in-any-order :id [(equals-map {:id (equals-value 1) :x (equals-value 1)}) (equals-map {:id (equals-value 2) :x (equals-value 2)})]) [{:id 2 :x 2} {:id 1 :x 1}])
      => [:match [{:id 2 :x 2} {:id 1 :x 1}]]
      (match (in-any-order :id [(equals-map {:id (equals-value 1) :x (equals-value 1)}) (equals-map {:id (equals-value 2) :x (equals-value 2)}) (equals-map {:id (equals-value 3) :x (equals-value 3)})]) [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}])
      => [:match [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]])

    (fact "captures multiple mismatches"
      (match (in-any-order :id [(equals-map {:id (equals-value 1) :a (equals-value 1)}) (equals-map {:id (equals-value 2) :a (equals-value 2)})])
             [{:id 1 :a 10} {:id 2 :a 200}])
      => [:mismatch [{:id 1 :a (model/->Mismatch 1 10)} {:id 2 :a (model/->Mismatch 2 200)}]])

    (fact "only matches when all expected matchers are matched by elements of the given sequence"
      (match (in-any-order :id [(equals-value 1) (equals-value 2) (equals-value 3)]) [1 2])
      => [:mismatch [1 2 (model/->Missing 3)]])

    ))


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
  (select? (equals-map {:id (equals-value 10) :a (equals-value 42)}) :id {:id 10 :a 1337}) => truthy

  )
