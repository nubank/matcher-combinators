# Change Log
All notable changes to this project will be documented in this file. This
change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## 3.9.1 / 2024-02-07
- Add license to POM file to release on Clojars.

## 3.9.0 / 2024-02-01
- Add `nested-equals` matcher, which always uses `equals` matcher at every level of nesting.

## 3.8.8 / 2023-09-04
- refine abbreviation logic to not descend into fully mismatched data, because
  there is nothing to filter out in such sub-elements and it can cause issues
  with datomic entities

## 3.8.7 / 2023-09-01
- introduce `matcher-combinators.config` namespace to toggle use of ansi color
  codes and the new output abbreviation mode.
- [Experimental] add `(matcher-combinators.config/enable-abbreviation!)`, an
  experimental feature to print only the mismatched parts of a data-structure
  while elliding the matched parts.
- fix more issues when using non-composite matchers (`m/regex`, `m/pred`, etc)
  inside `match-with`.

## 3.8.6 / 2023-07-18
- fix issue when using non-composite matchers (`m/regex`, `m/pred`, etc)
  inside `match-with`.
- fix issue where `match-with` misbehaves in ClojureScript

## 3.8.5 / 2023-03-24
- fix clj-kondo lint warnings for `match?` in Clojurescript

## 3.8.4 / 2023-03-06
- Deprecate support for Midje
  - deprecate the `matcher-combinators.midje` namespace and functions in it

## 3.8.3 / 2023-02-08
- Add support down to Clojure 1.8
- Switch from lein (project.clj) to tools.deps (deps.edn)
- Move midje dependency to a dev dependency
  - If you're using midje features, you should have an explicit dependency on it

## 3.8.2 / 2023-02-08
- Fix compatibility with CLJS test setups run in the browser

## 3.8.1 / 2023-02-08
- version with broken JAR artifact; don't use

## 3.8.0 / 2023-01-31
- Add `seq-of` matcher, which takes a matcher, successfully matching when each element matches the provided matcher.
- Add `any-of` matcher, which takes any number of matchers, successfully matching when at least one matches.
- Add `all-of` matcher, which takes any number of matchers, successfully matching when all match.
- Add 2-arity `pred` matcher where the second argument is a description text.
  - Useful for mismatch messages when the pred is an anonymous function.
- Allow globally configuring ANSI color emission via newly added `enable!` and `disable!` functions in `matcher-combinators.ansi-color`
- Export clj-kondo config to silence unresolved-symbol warnings on match? and thrown-match?

## [3.7.2]
- Address cljs warning about Exception after `via` matcher was added

## [3.7.1]
- Bump midje to 1.10.9

## [3.7.0]
- Bump midje to 1.10.7

## [3.6.0]
- add `via` matcher, which transforms the `actual` data-structure before applying the
  `expected` matcher.

## [3.5.1]
- warn that in-any-order is expensive

## [3.5.0]
- embeds matcher supports map like (associative, not sequential) actual values

## [3.4.0]
- Eliminate "already refers to" warning on `abs` for clojure-1.11.0

## [3.3.1]
- Bump midje from 1.10.3 -> 1.10.4

## [3.3.0]
- mismatch messages now wrap the actual (highlighted in red) and expected (highlighed in yellow) with `(actual ...)` and `(expected ...)` forms to improve readability.

## [3.2.1]
- fix cljs issue introduced in `3.2.0`

## [3.2.0]
- implement `mismtach` matcher that is satisfied when the provided `expected` doesn't match.

## [3.1.4]
- fix `undeclared Var matcher-combinators.matchers/Absent` warning introduced in `3.1.3`

## [3.1.3]
- fix issue where `absent` matcher doesn't work with `match-with`

## [3.1.2]
- fix cljs warning for undeclared var `decimal?`

## [3.1.1]
- fix bug using `within-delta` nested in `match-with` where expected value is a list

## [3.1.0]
- adapt `utils/within-delta?` to accept BigDecimal as `delta` argument

## [3.0.1]
- eliminate cljs warnings

## [3.0.0]
- add within-delta matcher (replaces match-roughly)
- add match-with matcher [#134](https://github.com/nubank/matcher-combinators/issues/134)
  - also reimplemented match-with?, match-roughly? etc in terms of match-with
  - the overrides map now supports predicates as keys
- deprecate `match-with?`, `match-equals?`, `match-roughly?` assert expressions
- deprecate `match-with`, `match-equals`, and `match-roughly` midje checkers

``` clojure
;; With this release, do this:
(match? (matchers/match-with [map? matchers/equals] <expected>) <actual>)

;; instead of this (deprecated, but still works)
(match-with? {clojure.lang.IPersistentMap matchers/equals} <expected> <actual>)
(match-equals? <expected> <actual>)

;; and this
(match? (matchers/within-delta <delta> <expected>) <actual>)
;; or this
(is (match? (matchers/match-with [number? (matchers/within-delta <delta>)]
                                 <expected>)
            <actual>))

;; instead of this
(match-roughly? <delta> <expected> <actual>)
```

### BREAKING CHANGE

We removed `matcher-combinators.utils/match-roughly` in 3.0.0. If you were
using it, you should use `matcher-combinators.matchers/within-delta`
instead. We've documented `matcher-combinators.utils` as "Internal use
only." for clarification.

## [2.1.1]
- fix issue matching `false` in the context of sets [#124](https://github.com/nubank/matcher-combinators/issues/124)

## [2.1.0]
- extend `Matcher` protocol to `Symbol` in cljs [#131](https://github.com/nubank/matcher-combinators/pull/131)

## [2.0.0]

- add `matchers/matcher-for` [#123](https://github.com/nubank/matcher-combinators/pull/123)
- use set matching logic for `java.util.Set` [#125](https://github.com/nubank/matcher-combinators/pull/125)
- add `core/indicates-match?` and deprecate `core/match?`
  - `core/match?` is mostly for internal use and occasionally used to build match?
    fns in other libraries

### BREAKING CHANGE

matcher-combinators-2.0.0 includes a breaking change for custom implementations of the
`matcher-combinators.core/Matcher` protocol:

- change the implementation of `match` to `-match` (required)
- add an implementation of `-matcher-for` (optional, but recommended)
  - should just return `this` e.g. `(-matcher-for [this] this)

## [1.5.2]
- fix double eval of `clojure.test` `match-equals?` arguments

## [1.5.1]
- refactor macro to align w/ Clojure docs (no behavioural change)

## [1.5.0]
- Implement default equality matching for objects. If classes don't explicity
  implement the `match` protocol, they now default to using equality matching.

## [1.4.0]
- add `matcher-combinators.standalone/match` (test-framework independent)

## [1.3.1]
- add arglist for cljtest assert expressions

## [1.3.0]
Add new matching context for clojure.test:
- `match-roughly?`: Matches all numerics as long as they are within a given delta of the expected.

## [1.2.7]
- Optionally allow omitting the first argument to `thrown-match?`: `(thrown-match? {:foo 1} (bang!)`

## [1.2.6]
- replace `+'` with `+` and `-'` with `-` in roughly matching
- Default to `equals` matcher for array-seq

## [1.2.5]
- Default to `equals` matcher for chunked-sequences

## [1.2.4]
- Default to `equals` matcher for URIs

## [1.2.3]
- Fix performance regression with order-agnostic matchers (`in-any-order`, `embeds`, `set-{embeds|equals}`)

## [1.2.2]
- Fix clojurescript import issue `No such namespace: clojure.test`

## [1.2.1]
- Fix issue where clojure.test mismatches wouldn't report the correct line number or file

## [1.2.0]
Add new matching contexts:
For Midje:
- `match-with`: takes a class->matcher map and an expected matcher. The map defines what matcher will be used by default when a particular class instance is found.
- `match-equals`: Allows for exact matching of datastructures by using the `equals` matcher instead of `embeds` for maps.
- `match-roughly`: Matches all numerics as long as they are within a given delta of the expected.

For `clojure.test`:
- `match-with?`: same as the midje version above
- `match-equals?`: same as the midje version above
As well as `matcher-combinator.test/build-match-assert` to help build new matcher contexts

## [1.1.0]
- Improve cider + cursive integration by using `:fail`, which is the standard in `clojure.test`
  for reporting mismatches. Thanks to Arne Brasseur (@plexus) for implementation

## [1.0.1]
- Provide clearer message on incorrect arg count to `match?` and `thrown-match?`

## [1.0.0]
- [BREAKING] When a record is in the `expected` position of the matcher, the
  `actual` value must be a record of the same type.

  Previous behavior would match if the `actual` was a map with the same keys,
  or a record of a different type with the same keys

## [0.9.0]
- matcher for asserting absence of key

## [0.8.4]
- fix cljs related warning in math.combinatorics

## [0.8.3]
- fix compatibility issue with shadow-cljs

## [0.8.2]
- fix slow matching behavior for `in-any-order` / `embeds`

## [0.8.1]
- declare `match?` to help avoid linters removing require

## [0.8.0]
- parser support for java classes

## [0.7.0]
- clojurescript support

## [0.6.1]
- parser support for vars

## [0.6.0]
- implement `throws-match` (midje) and `thrown-match?` (clojure.test) exception handling matchers

## [0.5.1]
- parser support for shorts

## [0.5.0]
- parser support for byte-arrays

## [0.4.2]
- parser support for LocalTime

## [0.4.1]
- multiple-arity fix for standalone matching api

## [0.4.0]
- api for using matching logic as a yes/no predicate, without access to mismatch info

## [0.3.4]
- fix typo in spec that causes errors when spec checking is enabled

## [0.3.3]
- make `in-any-order` choose smallest mismatch when same number of matchers one level down fail.

## [0.3.2]
- fix for `embeds` sequence matching where some matches weren't found

## [0.3.1]
- fix to get project working with cljdoc

## [0.3.0]
- regexes are now interpreted as matchers

## [0.2.8]
- fix issue where sequence mismatch was reported in reverse order (#39)
- fix issue matching core clojure sequence types like Repeat (#26)

## [0.2.7]
- fix issue where a 'missing' was showing as a 'mismatch'

## [0.2.6]
- namespace the custom `:mismatch` directive for `clojure.test` reporting

## [0.2.5]
- add default parser for Integer and Float
- make mismatches for sets and the `prefix` matcher more informative

## [0.2.4]
- fix issue when matching in the presence of midje metaconstants

## [0.2.3]
- fix issue matching empty sets

## [0.2.2]
- implement default matcher parser for sets

## [0.2.1]
- stop using `boolean?` which is only in clojure 1.9

## [0.2.0]
- _BREAKING_:
  - rename `equals-map`, `equals-seq`, and `equals-value` to all be `equals`
    and do dispatch based on type
  - rename `sublist` to `prefix`
  - rename `subset` sequence matcher to be `embeds`
  - rename `contains-map` to `embeds` and make it do dispatch based on type
- implement matchers for sets: `equals` and `embeds` as well as `set-equals`
  and `set-embeds` which allow one to use sequences to match sets, skirting the
  issue that a set matcher of form `#{odd? odd?}` will reduce to `#{odd?}`.


## [0.1.7]
- Adapt `in-any-order` to print diff for element ordering that leads to most direct matches
- When matcher is provided with incorrect input, cause matcher to fail, but don't raise exception

## [0.1.6-SNAPSHOT]
- Fix issue where `in-any-order` operating over a list with several identical matchers failed
- Extend `nil` to be interpreted as `equals-value` by parser
- Include needed dependencies outside of `:dev` profile
- Make mismatch output preserve sequence type

## [0.1.5-SNAPSHOT]
- Interpret Double as `matcher-combinators.matchers/equals-value` in parser.
- Make Midje checker fail correctly when passed a non-matcher

## [0.1.4-SNAPSHOT]
- Don't define `matcher-combinators.midje/matcher` as a macro; it isn't needed

## [0.1.3-SNAPSHOT]
- Allow inline use of `match` inside of `midje` `provided` forms

## [0.1.2-SNAPSHOT]
- Update parser to interpret lists as equals-seq matcher-combinators

## [0.1.1-SNAPSHOT]
- Fix `subset` issue where order of elements effected behavior

## [0.1.0-SNAPSHOT]
- Project init
