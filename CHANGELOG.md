# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

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
