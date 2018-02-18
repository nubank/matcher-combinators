(ns matcher-combinators.matchers
  (:require [matcher-combinators.core :as core]))

(defn equals
  "Matcher that will match when the given value is exactly the same as the
  `expected`."
  [expected]
  (cond
    (sequential? expected) (core/->EqualsSeq expected)
    (set? expected)        (core/->EqualsSet expected false)
    (map? expected)        (core/->EqualsMap expected)
    :else                  (core/->Value expected)))

(defn equals-set
  "Matches a set in the way `(equals some-set)` would, but accepts sequences as
  the expected matcher argument, allowing one to use matchers with the same
  submatcher appearing more than once."
  [expected]
  (core/->EqualsSet expected true))

(defn embeds
  "Matcher that will match when the map contains some of the same key/values as
  the `expected` map."
  [expected]
  (cond
    (sequential? expected) (core/->EmbedsSeq expected)
    (set? expected)        (core/->EmbedsSet expected false)
    (map? expected)        (core/->EmbedsMap expected)
    :else                  (core/->InvalidType expected "embeds" "seq, set, map")))

(defn embeds-set
  "Matches a set in the way `(embeds some-set)` would, but accepts sequences
  as the expected matcher argument, allowing one to use matchers with the same
  submatcher appearing more than once."
  [expected]
  (core/->EmbedsSet expected true))

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

(defn prefix-seq
  "Matcher that will match when provided a (ordered) prefix of the `expected`
  list.

  Similar to Midje's `(embeds expected)`"
  [expected]
  (core/->PrefixSeq expected))
