(ns matcher-combinators.standalone
  "An API for using matcher-combinators outside the context of a test framework"
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.parser]))

(defn match
  "Returns a map indicating whether the `actual` value matches `expected`.

  `expected` can be the expected value, a matcher, or a predicate fn of actual.

  Return map includes the following keys:

  - :match/result    - either :match or :mismatch
  - :mismatch/detail - the actual value with mismatch annotations. Only present when :match/result is :mismatch"
  [matcher actual]
  (let [{:keys [matcher-combinators.result/type
                matcher-combinators.result/value]}
        (core/match matcher actual)]
    (cond-> {:match/result type}
      (= :mismatch type)
      (assoc :mismatch/detail value))))

(defn match?
  "Given a `matcher` and `actual`, returns `true` if
  `(match matcher actual)` results in a match. Else, returns `false.`

  Given only a `matcher`, returns a function that will
  return true or false by the same logic."
  ([matcher]
   (fn [actual] (match? matcher actual)))
  ([matcher actual]
   (core/indicates-match? (core/match matcher actual))))
