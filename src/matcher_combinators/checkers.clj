(ns matcher-combinators.checkers
  (:require [matcher-combinators.parser]
            [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [midje.checking.core :as checking]
            [midje.checking.checkers.util :as checkers.util]
            [midje.checking.checkers.defining :as checkers.defining]
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

        (map? expected)
        (match-and-format (core/equals-map expected) actual)

        (vector? expected)
        (match-and-format (core/equals-sequence expected) actual)

        (satisfies? core/Matcher expected)
        (match-and-format expected actual)

        (checkers.defining/checker? expected)
        (match-and-format (core/pred->matcher expected) actual)

        :else
        (checking/as-data-laden-falsehood
          {:notes ["`match` couldn't parse `expected` input into a matcher-combinators"
                   "type of `expected` input:"
                   (type expected)]})))))

(sw/defchecker embeds [expected]
  (checkers.util/named-as-call "embeds" expected
    (sw/checker [actual]
      (cond
        (exception/captured-throwable? actual)
        (checking/as-data-laden-falsehood {:notes [(exception/friendly-stacktrace actual)]})

        (map? expected)
        (match-and-format (core/embeds-map expected) actual)

        (vector? expected)
        (match-and-format (core/equals-sequence expected) actual)

        (satisfies? core/Matcher expected)
        (match-and-format expected actual)

        (checkers.defining/checker? expected)
        (match-and-format (core/pred->matcher expected) actual)

        :else
        (checking/as-data-laden-falsehood
          {:notes ["`match` couldn't parse `expected` input into a matcher-combinators"
                   "type of `expected` input:"
                   (type expected)]})))))
