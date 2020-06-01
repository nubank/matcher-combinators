(ns matcher-combinators.test-helpers
  (:require [clojure.test.check.generators :as gen]
            [orchestra.spec.test :as spec.test]))

(defn instrument
  "Test fixture to turn on clojure.spec instrumentation."
  [t]
  (spec.test/instrument)
  (t)
  (spec.test/unstrument))

(def gen-any-equatable
  "Generates from gen/any-equatable such that there is no set in the
  resulting structure that contains the value `false`. This is to get
  around a bug which causes `(match? #{false} #{false})` to return
  false.  Until that is fixed, use this generator instead of
  gen/any-equatable.

  See https://github.com/nubank/matcher-combinators/issues/124"
  (gen/such-that
   (fn [v]
     (every? (fn [node] (or (not (set? node))
                            (not (contains? node false))))
             (tree-seq coll? #(if (map? %) (vals %) %) v)))
   gen/any-equatable))
