(ns matcher-combinators.standalone-test
  (:require [midje.sweet :as midje :refer [fact facts => falsey]]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [clojure.spec.test.alpha :as spec.test]))

(spec.test/instrument)

(fact "basic use of matchers with match?"
  (standalone/match? (m/in-any-order [1 2]) [1 2]) => true
  (standalone/match? (m/in-any-order [1 2]) [1 3]) => false)

(fact "the parser defaults still work"
  (standalone/match? (m/embeds {:a odd?}) {:a 1 :b 2}) => true
  (standalone/match? {:a odd?} {:a 1 :b 2}) => true
  (standalone/match? {:a odd?} {:a 2 :b 2}) => false)

(fact "using partial version of match?"
  ((standalone/match? (m/embeds {:a odd?})) {:a 1 :b 2})) => true

(spec.test/unstrument)
