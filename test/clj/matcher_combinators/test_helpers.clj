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
  (gen/such-that
   (fn [v]
     (every? (fn [node] (or (not (set? node))
                            (not (contains? node false))))
             (tree-seq coll? #(if (map? %) (keys %) %) v)))
   gen/any-equatable))
