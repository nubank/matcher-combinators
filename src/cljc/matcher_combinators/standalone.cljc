(ns matcher-combinators.standalone
  (:require [clojure.spec.alpha :as s]
            [matcher-combinators.core :as core]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]))

(defn match
  "Returns a map indicating whether the `actual` value matches `expected`.

  `expected` can be the expected value, a matcher, or a predicate fn of actual.

  Return map includes the following keys:

  - :match/result    - either :match or :mismatch
  - :mismatch/detail - the actual value with mismatch annotations. Only present when :match/result is :mismatch"
  [matcher actual]
  (let [{::result/keys [type value]}
        (core/match matcher actual)]
    (cond-> {:match/result type}
      (= :mismatch type)
      (assoc :mismatch/detail value))))

(def
  ^{:doc      (-> #'core/match? meta :doc)
    :arglists (-> #'core/match? meta :arglists)}
  match?
  core/match?)
