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

#?(:cljs (defn- cljs-uri [expected]
           (core/->CljsUriEquals expected)))

(defn matcher-for
  "Returns the type-specific matcher object for an expected
  value. This is used internally to support the match-with matcher,
  and is also useful for discovery when you want to know which Matcher
  type is associated to a value.

  Adds :matcher-object? metadata to the returned matcher so that
  other functions can differentiate between matcher objects and
  objects that happen to implement the Matcher protocol (which should
  be all other objects)."
  ([expected]
   (vary-meta
    (core/-matcher-for expected)
    assoc :matcher-object? true))
  ([expected overrides]
   (vary-meta
    (core/-matcher-for expected overrides)
    assoc :matcher-object? true)))

(defn match-with [overrides value]
  (cond (:matcher-object? (meta value))
        value
        (map? value)
        (matcher-for (reduce-kv (fn [m k v]
                                  (assoc m k (match-with overrides v)))
                                {}
                                value)
                     overrides)
        (coll? value)
        (matcher-for (reduce (fn [c v]
                               (conj c (match-with overrides v)))
                             (empty value)
                             value)
                     overrides)

        :else
        (matcher-for value overrides)))

(def type->matcher-defaults
  #?(:cljs {}
     :clj {clojure.lang.IPersistentMap embeds
           java.util.regex.Pattern     regex}))

(defn lookup-matcher
  "Internal use only. Merges type->matcher-overrides into
  type->matcher-defaults and uses type to lookup a matcher in the
  resulting map. If none is found, returns the equals matcher."
  [type type->matcher-overrides]
  (get (merge type->matcher-defaults type->matcher-overrides)
       type
       equals))
