# matcher-combinators

Library for making assertions about nested data structures.

_current version:_

[![Current Version](https://img.shields.io/clojars/v/nubank/matcher-combinators.svg)](https://clojars.org/nubank/matcher-combinators)

_docs:_
[Found on cljdoc](https://cljdoc.xyz/d/nubank/matcher-combinators/)

## Motivation

Clojure's built-in data structures get you a long way when trying to codify and solve difficult problems. A solid selection of core functions allow you to easily create and access core data structures. Unfortunately, this flexibility does not extend to testing: we seem to be missing a comprehensive yet extensible way to assert that the data fits a particular structure.

This library addresses this issue by providing composable matcher combinators that can be used as building blocks to test functions that evaluate to nested data-structures more effectively.

## Features

- Matchers for scalar and structural values
  - Good readability supported by default interpretations of Clojure types as matchers
- Pretty-printed diffs when the actual result doesn't match the expected matcher
- Integration with `clojure.test` and `midje`

## Usage

### `clojure.test`

Require the `matcher-combinators.test` namespace, which will extend `clojure.test`'s `is` macro to accept the `match?` and `thrown-match?` directives.

 - `match?`: The first argument should be the matcher-combinator represented the expected value, and the second argument should be the expression being checked.
 - `thrown-match?`: The first argument should be a throwable subclass, the second a matcher-combinators, and the third the expression being checked.

For example:

```clojure
(require '[clojure.test :refer [deftest is]]
         '[matcher-combinators.test] ;; adds support for `match?` and `thrown-match?` in `is` expressions
         '[matcher-combinators.matchers :as m])

(deftest test-matching-with-explicit-matchers
  (is (match? (m/equals 37) (+ 29 8)))
  (is (match? (m/regex #"fox") "The quick brown fox jumps over the lazy dog")))

(deftest test-matching-scalars
  ;; most scalar values are interpreted as an `equals` matcher
  (is (match? 37 (+ 29 8)))
  (is (match? "this string" (str "this" " " "string")))
  (is (match? :this/keyword (keyword "this" "keyword")))
  ;; regular expressions are handled specially
  (is (match? #"fox" "The quick brown fox jumps over the lazy dog")))

(deftest test-matching-sequences
  ;; A sequence is interpreted as an `equals` matcher, which specifies
  ;; count and order of matching elements. The elements, themselves,
  ;; are matched based on their types.
  (is (match? [1 3] [1 3]))
  (is (match? [1 odd?] [1 3]))
  (is (match? [#"red" #"violet"] ["Roses are red" "Violets are ... violet"]))

  ;; use m/prefix when you only care about the first n items
  (is (match? (m/prefix [odd? 3]) [1 3 5]))

  ;; use m/in-any-order when order doesn't matter
  (is (match? (m/in-any-order [odd? odd? even?]) [1 2 3])))

(deftest test-matching-sets
  ;; A set is also interpreted as an `equals` matcher.
  (is (match? #{1 2 3} #{3 2 1}))
  (is (match? #{odd? even?} #{1 2}))
  ;; use m/set-equals to repeat predicates
  (is (match? (m/set-equals [odd? odd? even?]) #{1 2 3})))

(deftest test-matching-maps
  ;; A map is interpreted as an `embeds` matcher, which ignores
  ;; un-specified keys
  (is (match? {:name/first "Alfredo"}
              {:name/first  "Alfredo"
               :name/last   "da Rocha Viana"
               :name/suffix "Jr."})))

(deftest test-matching-nested-datastructures
  ;; Maps, sequences, and sets follow the same semantics whether at
  ;; the top level or nested within a structure.
  (is (match? {:band/members [{:name/first "Alfredo"}
                              {:name/first "Benedito"}]}
              {:band/members [{:name/first  "Alfredo"
                               :name/last   "da Rocha Viana"
                               :name/suffix "Jr."}
                              {:name/first "Benedito"
                               :name/last  "Lacerda"}]
               :band/recordings []})))

(deftest exception-matching
  (is (thrown-match? clojure.lang.ExceptionInfo
                     {:foo 1}
                     (throw (ex-info "Boom!" {:foo 1 :bar 2})))))
```

### Midje:

The `matcher-combinators.midje` namespace defines the `match` and `throws-match` midje-style checkers. These should be used on the right-side of the midje `fact` check arrows (`=>`)

 - `match`: This checker is used to wrap a matcher-combinator asserts that the provided value satisfies the matcher.
 - `throws-match`: This checker wraps a matcher-combinator and optionally a throwable subclass. It asserts that an exception (of the given class) is raised and the `ex-data` satisfies the provided matcher.

For example:

```clojure
(require '[midje.sweet :refer :all]
         '[matcher-combinators.matchers :as m]
         '[matcher-combinators.midje :refer [match]])
(fact "matching a map exactly"
  {:a {:bb 1 :cc 2} :d 3} => (match (m/equals {:a (m/embeds {:bb 1}) :d 3}))
  ;; but when a map isn't immediately wrapped, it is interpreted as an `embeds` matcher
  ;; so you can write the previous check as:
  {:a {:bb 1 :cc 2} :d 3} => (match (m/equals {:a {:bb 1} :d 3})))

(fact "you can assert an exception is thrown "
  ;; Assert _some_ exception is raised and the ex-data inside satisfies the matcher
  (throw (ex-info "foo" {:foo 1 :bar 2})) => (throws-match {:foo 1})
  ;; Assert _a specific_ exception is raised and the ex-data inside satisfies the matcher
  (throw (ex-info "foo" {:foo 1 :bar 2})) => (throws-match ExceptionInfo {:foo 1}))
```

Note that you can also use the `match` checker to match arguments within midje's `provided` construct:

```clojure
(unfinished f)
(fact "using matchers in provided statements"
  (f [1 2 3]) => 1
  (provided
    (f (match [odd? even? odd?])) => 1))
```

## Matchers

### Default matchers

When an expected value isn't wrapped in a specific matcher the default interpretation is:
- number, date, and other scalar values: `equals`
- regex: `regex`
- sequential: `equals`
- set: `equals`
- map: `embeds`

You can use the `matcher-for` function to discover which matcher would be used
for a specific value, e.g.

``` clojure
(require '[matcher-combinators.matchers :as matchers])

(matchers/matcher-for {:this :map})
;; => {:expected {:this :map}}

(class *1)
;; => matcher_combinators.core.EmbedsMap
```

You can then use that matcher in place of a value, e.g.

``` clojure
(require '[matcher-combinators.standalone :as matcher-combinators])
(matcher-combinators/match
  (matchers/matcher-for {:this :map})
  {:this :map :and :then-some})
;; => {:match/result :match}
```

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

- `regex`: matches the `actual-value-found` when provided an `expected-regex` using `(re-find expected-regex actual-value-found)`

- `absent`: for use in the context of maps. Matches when the actual map is missing the key pointing to the `absent` matcher. For example `(is (match? {:a absent :b 1} {:b 1}))` matches but `(is (match? {:a absent :b 1} {:a 0 :b 1}))` won't.

### building new matchers

You can extend your data-types to work with `matcher-combinators` by implemented the [`Matcher` protocol](https://github.com/nubank/matcher-combinators/blob/066da1a07ab620a6c63bbb0ce8e1b6b3a4ccd956/src/matcher_combinators/core.clj#L5-L9).

An example of this in the wild can be seen in the `abracad` library [here](https://github.com/nubank/abracad/blob/b52e6a7114461f50bdacc2cf09a1de08f707b9f3/test/abracad/custom_types_test.clj#L15-L20).

## Overriding default matchers

Inside the context of `match?` (clojure.test) / `match` (midje), data-structures are assigned default matchers, which eliminates the need to wrap data-structures with matcher-combinators when your desired matching behavior matches the defaults.

But what if your desired matching behavior deviates from the defaults?

For example, if you want to do exact map matching you need to use a log of `m/equals`:

```clojure
(deftest exact-map-matching-by-hand
  (is (match? (m/equals {:a (m/equals {:b (m/equals {:c odd?})})}))
              {:a {:b {:c 1}}})
  ;; without m/equals, the system defaults to m/embeds for maps,
  ;; which has looser matching properties
  (is (match? {:a {:b {:c odd?}}}
              {:a {:b {:c 1 :extra-c 0} :extra-b 0} :extra-a 0})))
```

This verbosity can be avoided by redefining the matcher data-type defaults

### clojure.test

You can register a custom `clojure.test` match assert expression if you are going to use it a few times:

```clojure
(defmethod clojure.test/assert-expr 'match-equals? [msg form]
  (matcher-combinators.test/build-match-assert 'match-equals? {map? m/equals} msg form))

(deftest match-equals-test
  (is (match-equals? {:a {:b {:c odd?}}}
                     {:a {:b {:c 1}}})))
```

Or if you want a one-off override of defaults, it can be done `match-with?`:

```
(deftest one-off-match-equals
  (is (match-with? {map? m/equals}
                   {:a {:b {:c odd?}}}
                   {:a {:b {:c 1}}})))
```

#### built-in matching context

- `match?`
- `match-with?`
- `match-equals?`
- `match-roughly?`:

```clojure
(deftest match-roughly-test
  (is (match-roughly? 0.1
                      {:a 1 :b 3.0}
                      {:a 1 :b 3.05})))
```

### midje

```clojure
(fact "match-with example"
  {:a {:b {:c odd?}}} => (match-with {map? m/equals}
                                     {:a {:b {:c odd?}}}))
(fact "match-equals example"
  {:a {:b {:c odd?}}} => (match-equals {:a {:b {:c odd?}}}))
```

#### built-in matching context

- `match`
- `match-with`
- `match-roughly`
- `match-equals`

Or you can build your own, for example:

```clojure
(def match-equals
  "match but using strict `equals` matching behavior for maps, even nested ones."
  (match-with {map? matchers/equals}))
```

## Running tests

The project contains `midje`, `clojure.test`, and `cljs.test` tests.

To run Clojure tests:

```
lein midje
```

To run Clojurescript tests:

```
lein test-node
```
