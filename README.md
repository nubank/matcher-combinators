# matcher-combinators

Library for creating matcher combinator to compare nested data structures

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

- `subset`:
- `sublist`:
- `equals-map`:
- `contains-map`:
- `equals-seq`:
- `in-any-order`:
- `equals-value`:
