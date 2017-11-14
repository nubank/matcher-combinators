(ns matcher-combinators.checkers
  (:require [matcher-combinators.parser]
            [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [midje.checking.core :as checking]
            [midje.checking.checkers.util :as checkers.util]
            [midje.util.exceptions :as exception]
            [midje.sweet :as sw]))

(defn- match-and-format [expected actual]
  (let [result (core/match expected actual)]
    (if (core/match? result)
      true
      (checking/as-data-laden-falsehood {:notes [(printer/print result)]}))))

(sw/defchecker match [expected]
  (checkers.util/named-as-call "match" expected
    (sw/checker [actual]
      (cond
        (exception/captured-throwable? actual)
        (checking/as-data-laden-falsehood {:notes [(exception/friendly-stacktrace actual)]})

        (satisfies? core/Matcher expected)
        (match-and-format expected actual)

        :else
        (checking/as-data-laden-falsehood
          {:notes ["`match` couldn't parse `expected` input into a matcher-combinators"
                   "type of `expected` input:"
                   (type expected)]})))))
