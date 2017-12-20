(ns matcher-combinators.matchers
  (:require [matcher-combinators.core :as core]))

(defn equals-value
  "Matcher that will match when the given value is exactly the same as the
  `expected`."
  [expected]
  (core/->Value expected))

(defn contains-map
  "Matcher that will match when the map contains some of the same key/values as
  the `expected` map."
  [expected]
  (core/->ContainsMap expected))

(defn equals-map
  "Matcher that will match when:
    1. the keys of the `expected` map are equal to the given map's keys
    2. the value matchers of `expected` map matches the given map's values"
  [expected]
  (assert (map? expected))
  (core/->EqualsMap expected))

(defn equals-seq
  "Matcher that will match when the `expected` list's matchers match the given list.

  Similar to midje's `(just expected)`"
  [expected]
  (assert (vector? expected))
  (core/->EqualsSequence expected))

(defn in-any-order
  "Matcher that will match when the given a list that is the same as the
  `expected` list but with elements in a different order.

  `select-fn`: optional argument used to anchoring specific substructures to
               clarify mismatch output

  Similar to Midje's `(just expected :in-any-order)`"
  ([expected]
   (core/->InAnyOrder expected))
  ([select-fn expected]
   (core/->SelectingInAnyOrder select-fn expected)))

(defn sublist
  "Matcher that will match when provided a (ordered) prefix of the `expected`
  list.

  Similar to Midje's `(contains expected)`"
  [expected]
  (assert (vector? expected))
  (core/->SubSeq expected))

(defn subset
  "Order-agnostic matcher that will match when provided a subset of the
  `expected` list.

  Similar to Midje's `(contains expected :in-any-order :gaps-ok)`"
  [expected]
  (assert (vector? expected))
  (core/->SubSet expected))
