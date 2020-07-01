(ns matcher-combinators.test-helpers
  (:require [clojure.test.check.generators :as gen]
            #?(:cljs [clojure.spec.test.alpha :as spec.test]
               :clj  [orchestra.spec.test :as spec.test])
            [matcher-combinators.core :as core]))

(defn instrument
  "Test fixture to turn on clojure.spec instrumentation."
  [t]
  (spec.test/instrument)
  (t)
  (spec.test/unstrument))

(defn greater-than-matcher [expected-long]
  (core/->PredMatcher
   (fn [actual] (> actual expected-long))
   (str "greater than " expected-long)))
