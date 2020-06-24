(ns matcher-combinators.matchers
  (:require [clojure.string :as string]
            [matcher-combinators.core :as core]))

(defn- non-internal-record? [v]
  (and (record? v)
       (not (string/starts-with? (-> v type str) "class matcher_combinators.core"))))

(defn equals
  "Matcher that will match when the given value is exactly the same as the
  `expected`."
  [expected]
  (cond
    (sequential? expected)          (core/->EqualsSeq expected)
    (set? expected)                 (core/->SetEquals expected false)
    (non-internal-record? expected) (core/->EqualsRecord expected)
    (map? expected)                 (core/->EqualsMap expected)
    :else                           (core/->Value expected)))

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
    (sequential? expected)          (core/->EmbedsSeq expected)
    (set? expected)                 (core/->SetEmbeds expected false)
    (non-internal-record? expected) (core/->EqualsRecord expected)
    (map? expected)                 (core/->EmbedsMap expected)
    :else                           (core/->InvalidType expected "embeds" "seq, set, map")))

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

(defn pred
  "Matcher that will match when `pred` of the actual value returns true."
  [pred]
  (core/->PredMatcher pred (str "predicate: " pred)))

(defn matcher-for
  "Returns the type-specific matcher object for an expected value. This is
  useful for discovery when you want to know which Matcher type is associated
  to a value."
  [expected]
  (core/-matcher-for expected))

#?(:cljs (defn- cljs-uri [expected]
           (core/->CljsUriEquals expected)))
