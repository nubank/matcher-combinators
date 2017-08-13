(ns matcher-combinators.core-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.core :refer :all]))

(facts "on the leaf values matcher: v"
  (match (equals-value 42) 42) => [:match 42]
  (match (equals-value 42) 43) => [:mismatch (->Mismatch 42 43)]
  (fact "value missing"
    (match (equals-value 42) nil) => [:mismatch (->Missing 42)]))


(facts "on the contains-map matcher"
  (fact "perfect match"
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 1337}) => [:match {:a 42, :b 1337}])

  (fact "value mismatches"
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 1337}) => [:mismatch {:a (->Mismatch 42 43), :b 1337}]
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 13373}) => [:mismatch {:a 42, :b (->Mismatch 1337 13373)}]
    (match (contains-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 13373}) => [:mismatch {:a (->Mismatch 42 43), :b (->Mismatch 1337 13373)}])


  (fact "missing expected keys"
    (match (contains-map {:a (equals-value 42)}) {:b 42}) => [:mismatch {:b 42, :a (->Missing 42)}]
    (match (contains-map {:a (equals-value 42) :b (equals-value 42)}) {:b 42}) => [:mismatch {:b 42, :a (->Missing 42)}])

  (facts "nesting contains-map"
    (match (contains-map {:a (equals-value 42) :m (contains-map {:x (equals-value "foo")})}) {:a 42 :m {:x "foo"}})
    => [:match {:a 42 :m {:x "foo"}}]


    (match (contains-map {:a (equals-value 42)
                    :m (contains-map {:x (equals-value "foo")})})
      {:a 42
       :m {:x "bar"}})
    => [:mismatch {:a 42
                   :m {:x (->Mismatch "foo" "bar")}}]

    (match (contains-map {:a (equals-value 42)
                    :m (contains-map {:x (equals-value "foo")})})
      {:a 43
       :m {:x "bar"}})
    => [:mismatch {:a (->Mismatch 42 43)
                   :m {:x (->Mismatch "foo" "bar")}}]

    (fact "when not given a map"
      (match (contains-map {:a (equals-value 10)}) 10) => [:mismatch (->Mismatch {:a (equals-value 10)} 10)])))

(facts "on the equals-map matcher"
  (fact "perfect match"
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 1337}) => [:match {:a 42, :b 1337}])

  (fact "value mismatches"
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 1337}) => [:mismatch {:a (->Mismatch 42 43), :b 1337}]
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 42, :b 13373}) => [:mismatch {:a 42, :b (->Mismatch 1337 13373)}]
    (match (equals-map {:a (equals-value 42), :b (equals-value 1337)}) {:a 43, :b 13373}) => [:mismatch {:a (->Mismatch 42 43), :b (->Mismatch 1337 13373)}])

  (fact "missing expected keys"
    (match (equals-map {:a (equals-value 42) :b (equals-value 42)}) {:b 42}) => [:mismatch {:b 42, :a (->Missing 42)}]


  (fact "observing extra keys"
    (match (equals-map {:a (equals-value 42)}) {:a 42 :b 1337})
    => [:mismatch {:a 42, :b (->Unexpected 1337)}]

    (match (equals-map {:a (equals-value 42)}) {:b 42})
    => [:mismatch {:b (->Unexpected 42), :a (->Missing 42)}]))

  (future-fact "when not given a map"
    (match (equals-map {:a (equals-value 10)}) 10) => [:mismatch (->Mismatch {:a 10} 10)]))

(facts "on the equals-sequence matcher"
  (fact "perfect match"
    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [1 2])
    => [:match [1 2]])

  (fact "element mismatches"
    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [2 1])
    => [:mismatch [(->Mismatch 1 2) (->Mismatch 2 1)]]

    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [1 3])
    => [:mismatch [1 (->Mismatch 2 3)]])

  (fact "actual more than expected"
    (match (equals-sequence [(equals-value 1) (equals-value 2)]) [1 2 3])
    => [:mismatch [1 2 (->Unexpected 3)]])

  (facts "on nesting equals-sequence matchers"
    (match (equals-sequence [(equals-sequence [(equals-value 1) (equals-value 2)]) (equals-value 20)]) [[1 2] 20])
    => [:match [[1 2] 20]]

    (match (equals-sequence [(equals-sequence [(equals-value 1) (equals-value 2)]) (equals-value 20)]) [[1 5] 20])
    => [:mismatch [[1 (->Mismatch 2 5)] 20]]

    (match (equals-sequence [(equals-sequence [(equals-value 1) (equals-value 2)]) (equals-value 20)]) [[1 5] 21])
    => [:mismatch [[1 (->Mismatch 2 5)] (->Mismatch 20 21)]])
  )

(facts "on nesting multiple matchers"
  ;(match (equals-sequence [(equals-map {:a (equals-value 42), :b (equals-value 1337)}) (equals-value 20)]) [[1 5] 20])
  )

(def ms [(equals-value 1) (equals-value 2)])
(map #(partial match %) ms)
