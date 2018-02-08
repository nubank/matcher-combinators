# matcher-combinators

Library for creating matcher combinator to compare nested data structures

Alpha version:

[![Current Version](https://img.shields.io/clojars/v/nubank/matcher-combinators.svg)](https://clojars.org/nubank/matcher-combinators)

__Note:__ currently in alpha; function names and namespaces are subject to change. When you encounter bugs, please file an issue with reproduction steps and we'll look into them as soon as possible.

## Motivation

Clojure's built-in data structures get you a long way when trying to codify and solve difficult problems. A solid selection of core functions allow you to easily create and access core data structures. Unfortunately, this flexibility does not extend to testing: a comprehensive yet extensible way to assert that the data fits a particular structure seems to be lacking.

This library address this issue by providing composable matcher combinators that can be used as building blocks to effectively test functions that evaluate to nested data-structures.

## Features

- Pretty-printed diffs when the actual result doesn't match the expected matcher
- Integrates with clojure.test and midje
- Good readability by providing default interpretations of core clojure data-structures as matcher combinators

## Usage

### Midje:

The `matcher-combinators.midje` namespace defines the `match` midje-style checker. This checker is used to wrap matcher-combinators to be used on the right-side of the fact check arrows

For example:

```clojure
(require '[midje.sweet :refer :all]
         '[matcher-combinators.matchers :as m]
         '[matcher-combinators.midje :refer [match]])
(fact "matching a map exactly"
  {:a {:bb 1} :c 2} => (match (m/equals-map {:a {:bb 1} :c 2})))

(fact "loosely matching a map"
  ;; by default a map is interpreted as a `contains-map` matcher
  {:headers {:type "txt"} :body "hello world!"} => (match {:body string?}))
```

Note that you can also use the `match` checker to match arguments within midje's `provided` construct:

```clojure
(unfinished f)
(fact "using matchers in provided statements"
  (f [1 2 3]) => 1
  (provided
    (f (match [odd? even? odd?])) => 1))
```

### `clojure.test`

Require the `matcher-combinators.test` namespace, which will extend `clojure.test`'s `is` macro to accept the `match?` directive. The first argument to `match?` should be the matcher-combinator represented the expected value, and the second argument should be the actual value being checked.

For example:

```clojure
(require '[clojure.test :refer :all]
         '[matcher-combinators.test] ;; needed for defining `match?`
         '[matcher-combinators.matchers :as m])
(deftest basic-sequence-matching
  ;; by default a vector is interpreted as a `equals-seq` matcher
  (is (match? [1 odd?] [1 3]))
  (is (match? (m/sublist [1 odd?]) [1 1 2 3])))
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
- `equals-seq`: matches when the `expected` list's matchers match the given list. Similar to midje's `(just expected)`
- `subset`: order-agnostic matcher that will match when provided a subset of the `expected` list. Similar to midje's `(contains expected :in-any-order :gaps-ok)`
- `sublist`: matches when provided a (ordered) prefix of the `expected` list.  Similar to midje's `(contains expected)`
- `in-any-order`: matches when the given a list that is the same as the `expected` list but with elements in a different order.  Similar to midje's `(just expected :in-any-order)`
- `contains-map`: matches when the map contains some of the same key/values as the `expected` map.

## Running tests

The project contains both midje and `clojure.test` tests.

Midje is capable of running both types of tests:

```
lein midje
```
