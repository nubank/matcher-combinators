(ns matcher-combinators.test-helpers
  (:require [matcher-combinators.core :as core]))

(defn abs-value-matcher [expected]
  (core/->PredMatcher
   (fn [actual] (= (Math/abs expected)
                   (Math/abs actual)))
   (str "equal to abs value of " expected)))
