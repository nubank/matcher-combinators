(ns matcher-combinators.midje
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.parser]
            [matcher-combinators.printer :as printer]
            [midje.checking.core :as checking]
            [midje.util.exceptions :as exception]
            [midje.checking.checkers.defining :as checkers.defining])
  (:import [matcher_combinators.core Matcher]))

(defn check-match [matcher actual]
  (if (exception/captured-throwable? actual)
    (checking/as-data-laden-falsehood
      {:notes [(exception/friendly-stacktrace actual)]})
    (let [result (core/match matcher actual)]
      (if (core/match? result)
        true
        (checking/as-data-laden-falsehood {:notes [(printer/as-string result)]})))))

(checkers.defining/defchecker match [matcher]
  (checkers.defining/checker [actual]
    (if (core/matcher? matcher)
      (check-match matcher actual)
      (checking/as-data-laden-falsehood
        {:notes [(str "Input wasn't a matcher: " matcher)]}))))
