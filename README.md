# matcher-combinators

Library for creating matcher combinator to compare nested data structures

Alpha version:

[![Current Version](https://img.shields.io/clojars/v/nubank/matcher-combinators.svg)](https://clojars.org/nubank/matcher-combinators)

__Note:__ currently in beta, we would appreciate it if you took the time to file issues for any bugs you may.

## Motivation

Clojure's built-in data structures get you a long way when trying to codify and solve difficult problems. A solid selection of core functions allow you to easily create and access core data structures. Unfortunately, this flexibility does not extend to testing: a comprehensive yet extensible way to assert that the data fits a particular structure seems to be lacking.

This library address this issue by providing composable matcher combinators that can be used as building blocks to effectively test functions that evaluate to nested data-structures.

## Features

- Pretty-printed diffs when the actual result doesn't match the expected matcher
- Integrates with clojure.test and midje
- Good readability by providing default interpretations of core clojure data-structures as matcher combinators

| Midje checkers | Matcher combinators |
| ------- | ----- |
| ![midje checkers](doc/images/midje_check.png) | ![matcher combinators check](doc/images/matcher_check.png) |

| Midje checkers failure output | Matcher combinators failure output |
| ------- | ----- |
| ![midje checker failure output](doc/images/midje_failure.png) | ![matcher combinators failure output](doc/images/matcher_output.png) |

## Usage

### Midje:

The `matcher-combinators.midje` namespace defines the `match` midje-style checker. This checker is used to wrap matcher-combinators to be used on the right-side of the fact check arrows

For example:

```clojure
(require '[midje.sweet :refer :all]
         '[matcher-combinators.matchers :as m]
         '[matcher-combinators.midje :refer [match]])
(fact "matching a map exactly"
  {:a {:bb 1} :c 2} => (match (m/equals {:a {:bb 1} :c 2})))

(fact "loosely matching a map"
  ;; by default a map is interpreted as a `embeds` matcher
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
  (is (match? (m/prefix [1 odd?]) [1 1 2 3])))
```

## Matchers

### default matchers

If a data-structure isn't wrapped in a specific matcher-combinator the default interpretation is:
- map: `embeds`
- vector: `equals`
- number, date, and other base data-structure: `equals`

### built-in matchers

- `equals` operates over base values, maps, sequences, and sets

  - base values (string, int, function, etc.): matches when the given value is exactly the same as the `expected`.
  - map: matches when
      1. the keys of the `expected` map are equal to the given map's keys
      2. the value matchers of `expected` map matches the given map's values
  - sequence: matches when the `expected` sequences's matchers match the given sequence. Similar to midje's `(just expected)`
  - set: matches when all the elements in the given set can be matched with a matcher in `expected` set and each matcher is used exactly once.
- `embeds` operates over maps, sequences, and sets
  - map: matches when the map contains some of the same key/values as the `expected` map.
  - sequence: order-agnostic matcher that will match when provided a subset of the `expected` sequence. Similar to midje's `(contains expected :in-any-order :gaps-ok)`
  - set: matches when all the matchers in the `expected` set can be matched with an element in the provided set. There may be more elements in the provided set than there are matchers.
- `prefix` operates over sequences

  matches when provided a (ordered) prefix of the `expected` sequence. Similar to midje's `(contains expected)`
- `in-any-order` operates over sequences

  matches when the given a sequence that is the same as the `expected` sequence but with elements in a different order.  Similar to midje's `(just expected :in-any-order)`

- `set-equals`/`set-embeds` similar behavior to `equals`/`embeds` for sets, but allows one to specify the matchers using a sequence so that duplicate matchers are not removed. For example, `(equals #{odd? odd?})` becomes `(equals #{odd})`, so to get arround this one should use `(set-equals [odd? odd])`.

## Running tests

The project contains both midje and `clojure.test` tests.

Midje is capable of running both types of tests:

```
lein midje
```
