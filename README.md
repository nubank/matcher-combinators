# matcher-combinators

Library for creating matcher combinator to compare nested data structures

Alpha version:

`[nubank/matcher-combinators "0.1.0-SNAPSHOT"]`

__Note:__ currently in alpha; function names and namespaces are subject to change

## Usage

### Midje:

The `matcher-combinators.midje` namespace defines the `match` midje-style checker. This checker is used to wrap matcher-combinators to be used on the right-side of the fact check arrows

For example:

```clojure
(require '[midje.sweet :refer :all]
         '[matcher-combinators.matchers :as m]
         '[matcher-combinators.midje :refer [match]])
(fact "sequence matching"
  {:a {:bb 1} :c 2} => (match (m/equals-map {:a {:bb 1} :c 2})))
```

Note that you can also use the `match` checker to match arguments within Midje's `provided` construct:

```clojure
(unfinished f)
(let [alternating-short-list (match [odd? even? odd?])]
  (fact "using matchers in provided statements"
    (f [1 2 3]) => 1
    (provided
      (f alternating-short-list) => 1)))
```

### `clojure.test`

Require the `matcher-combinators.test` namespace, which will extend `clojure.test`'s `is` macro to accept the `match?` directive. The first argument to `match?` should be the matcher-combinator represented the expected value, and the second argument should be the actual value being checked.

For example:

```clojure
(require '[clojure.test :refer :all]
         '[matcher-combinators.test] ;; needed for defining `match?`
         '[matcher-combinators.matchers :as m])
(deftest basic-matching
  (is (match? (m/equals-seq [1 odd?]) [1 3])))
```

## Matchers

### default matchers

If a data-structure isn't wrapped in a specific matcher-combinator the default interpretation is:
- map: `contains-map`
- vector: `equals-seq`
- number, date, and other base data-structure: `equals-value`

### built-in matchers

- `equals-value`: matches when the given value is exactly the same as the `expected`.
- `equals-map`: matches when:
      1. the keys of the `expected` map are equal to the given map's keys
      2. the value matchers of `expected` map matches the given map's values
- `equals-seq`: matches when the `expected` list's matchers match the given list. Similar to Midje's `(just expected)`
- `subset`: order-agnostic matcher that will match when provided a subset of the `expected` list. Similar to Midje's `(contains expected :in-any-order :gaps-ok)`
- `sublist`: matches when provided a (ordered) prefix of the `expected` list.  Similar to Midje's `(contains expected)`
- `in-any-order`: matches when the given a list that is the same as the `expected` list but with elements in a different order.  Similar to Midje's `(just expected :in-any-order)`
- `contains-map`: matches when the map contains some of the same key/values as the `expected` map.

## Tests

The project contains both Midje and `clojure.test` tests.

Midje is capable of running both types of tests:

```
lein midje
```
