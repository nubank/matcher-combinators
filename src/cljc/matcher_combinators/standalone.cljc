(ns matcher-combinators.standalone
  (:require [clojure.spec.alpha :as s]
            [matcher-combinators.core :as core]
            [matcher-combinators.parser]))

(defn match
  "Returns a map indicating whether the `actual` value matches the `matcher`.

  `matcher` can be a matcher-combinators matcher, a predicate function of
  actual, an expression that returns a value, or a literal value.

  `actual` can be an expression that returns the actual value, or a literal.

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

(s/fdef match?
  :args (s/alt :partial (s/cat :matcher (partial satisfies? core/Matcher))
               :full    (s/cat :matcher (partial satisfies? core/Matcher)
                               :actual any?))
  :ret (s/or :partial fn?
             :full boolean?))

(defn match?
  "Given a `matcher` and `actual`, returns `true` if
  `(match matcher actual)` results in a match. Else, returns `false.`

  Given only a `matcher`, returns a function that will
  return true or false by the same logic."
  ([matcher]
   (fn [actual] (match? matcher actual)))
  ([matcher actual]
   (core/match? (core/match matcher actual))))
