(ns matcher-combinators.matchers
  (:require [clojure.string :as string]
            [matcher-combinators.core :as core]
            [matcher-combinators.utils :as utils]
            #?(:cljs [matcher-combinators.core :refer [Absent]]))
  #?(:clj (:import [matcher_combinators.core Absent])))

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

(defn not-matcher
  [expected]
  (let [not-match? (fn [actual] (not (core/indicates-match? (core/match expected actual))))]
    (core/->PredMatcher not-match?
                        (str "expected mismatch from: " expected))))

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
   (core/-matcher-for expected))
  ([expected overrides]
   (core/-matcher-for expected overrides)))

(defn- ->pred [class-or-pred]
  (if (fn? class-or-pred)
    class-or-pred
    (partial instance? class-or-pred)))

(defn- format-overrides [overrides]
  (if (sequential? overrides)
    (partition 2 overrides)
    overrides))

(defn lookup-matcher
  "Internal use only. Iterates through pred->matcher-overrides and
  returns the value (a matcher) bound to the first pred that returns
  true for value. If no override is found, returns the default matcher
  for value.

  The legacy API called for a map of type->matcher, which is still
  supported by wrapping types in (instance? type %) predicates."
  [value pred->matcher-overrides]
  (or (->> (format-overrides pred->matcher-overrides)
           (filter (fn [[class-or-pred matcher]] (when ((->pred class-or-pred) value) matcher)))
           first
           last)
      (matcher-for value)))

(declare match-with)

(defn- match-with-values [m overrides]
  (reduce-kv (fn [m* k v] (assoc m* k (match-with overrides v)))
             {}
             m))

(defn- match-with-elements [coll overrides]
  (reduce (fn [c v] (conj c (match-with overrides v)))
          (if (set? coll)
            #{}
            [])
          coll))

(defn match-with
  "Given a vector (or map) of overrides, returns the appropriate matcher
  for value (with value wrapped). If no matcher for value is found in
  overrides, uses the default:
    embeds for maps
    regex  for regular expressions
    equals for everything else

  If value is a collection, recursively applies match-with to its nested
  values, ignoring nested values that are already wrapped in matchers.

  NOTE that each nested match-with creates a new context, and nested contexts
  do not inherit the overrides of their parent contexts."
  [overrides value]
  (vary-meta
         ;; don't re-wrap a value we've already wrapped
   (cond (::match-with? (meta value))
         value

         ;; functions are special because they get treated as predicates
         (fn? value)
         value

         ;; TODO: all of the built in matchers are records, but users
         ;; define matchers by reifying the Matcher protocol, so this
         ;; would break down. Also, what if a user's domain includes a
         ;; record with an `:expected` key? Ideally, we should have
         ;; some other marker to identify a matcher object, and document
         ;; it in terms of "your custom Matcher implementations must do
         ;; x in order to particpate in match-with"
         (and (record? value) (map? (:expected value)))
         (update value :expected match-with-values overrides)

         (and (record? value) (coll? (:expected value)))
         (update value :expected match-with-elements overrides)

         (= Absent (type value))
         value

         (map? value)
         ((matcher-for value overrides)
          (match-with-values value overrides))

         (coll? value)
         ((matcher-for value overrides)
          (match-with-elements value overrides))

         :else
         ((matcher-for value overrides) value))
   assoc ::match-with? true))

(defn within-delta
  "Given `delta` and `expected`, returns a Matcher that will match
  when the actual value is within `delta` of `expected`. Given only
  `delta`, returns a function to be used in the context of `match-with`,
  e.g.

    (is (match? (m/match-with [number? (m/within-delta 0.01M)]
                              <expected>)
                <actual>))"
  ([delta]
   (fn [expected] (within-delta delta expected)))
  ([delta expected]
   (core/->PredMatcher
    (fn [actual] (utils/within-delta? delta expected actual))
    (str "within-delta " expected " (+/- " delta ")"))))
