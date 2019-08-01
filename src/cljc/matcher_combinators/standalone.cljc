(ns matcher-combinators.standalone
  (:require [clojure.spec.alpha :as s]
            [matcher-combinators.core :as core]
            [matcher-combinators.parser]))

(s/fdef match?
  :args (s/alt :partial (s/cat :matcher (partial satisfies? core/Matcher))
               :full    (s/cat :matcher (partial satisfies? core/Matcher)
                               :actual any?))
  :ret boolean?)

(defn match?
  "Does the value match the provided matcher-combinator?"
  ([matcher]
   (fn [actual] (match? matcher actual)))
  ([matcher actual]
   (-> matcher
       (core/match actual)
       core/match?)))
