(ns matcher-combinators.matchers
  (:require [matcher-combinators.core :as core]))

(defn equals
  "Matcher that will match when the given value is exactly the same as the
  `expected`."
  [expected]
  (cond
    (sequential? expected) (core/->EqualsSeq expected)
    (set? expected)        (core/->SetEquals expected false)
    (record? expected)     (core/->EqualsRecord expected)
    (map? expected)        (core/->EqualsMap expected)
    :else                  (core/->Value expected)))

(defn set-equals
  "Matches a set in the way `(equals some-set)` would, but accepts sequences as
  the expected matcher argument, allowing one to use matchers with the same
  submatcher appearing more than once."
  [expected]
  (core/->SetEquals expected true))

(defn embeds
  "Matcher that will match when the map contains some of the same key/values as
  the `expected` map."
  [expected]
  (cond
    (sequential? expected) (core/->EmbedsSeq expected)
    (set? expected)        (core/->SetEmbeds expected false)
    (record? expected)     (core/->EmbedsRecord expected)
    (map? expected)        (core/->EmbedsMap expected)
    :else                  (core/->InvalidType expected "embeds" "seq, set, map")))

(defn set-embeds
  "Matches a set in the way `(embeds some-set)` would, but accepts sequences
  as the expected matcher argument, allowing one to use matchers with the same
  submatcher appearing more than once."
  [expected]
  (core/->SetEmbeds expected true))

(defn in-any-order
  "Matcher that will match when the given a list that is the same as the
  `expected` list but with elements in a different order.

  Similar to Midje's `(just expected :in-any-order)`"
  [expected] (core/->InAnyOrder expected))

(defn prefix
  "Matcher that will match when provided a (ordered) prefix of the `expected`
  list.

  Similar to Midje's `(embeds expected)`"
  [expected]
  (core/->Prefix expected))

(defn regex
  "Matcher that will match when given value matches the `expected` regular expression."
  [expected]
  (core/->Regex expected))

(def absent
  "Value-position matcher for maps that matches when containing map doesn't have the key pointing to this matcher."
  (core/->Absent))
