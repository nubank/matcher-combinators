(ns matcher-combinators.standalone
  (:require [clojure.spec.alpha :as s]
            [matcher-combinators.core :as core]
            [matcher-combinators.parser]))

(s/fdef match?
  :args (s/cat :matcher (partial satisfies? core/Matcher)
               :actual any?)
  :ret boolean?)

(defn match?
  "Does the value match the provided matcher-combinator?"
  [matcher actual]
  (-> matcher
      (core/match actual)
      core/match?))
