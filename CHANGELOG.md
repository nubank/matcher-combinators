# Change Log
All notable changes to this project will be documented in this file. This
change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

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
