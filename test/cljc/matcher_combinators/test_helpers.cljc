(ns matcher-combinators.test-helpers
  (:require #?(:cljs [clojure.spec.test.alpha :as spec.test]
               :clj  [orchestra.spec.test :as spec.test])
            [matcher-combinators.core :as core]))

(defn instrument
  "Test fixture to turn on clojure.spec instrumentation."
  [t]
  (spec.test/instrument)
  (t)
  (spec.test/unstrument))

(defn abs-value-matcher [expected]
  (core/->PredMatcher
   (fn [actual] (= (Math/abs expected)
                   (Math/abs actual)))
   (str "equal to abs value of " expected)))
