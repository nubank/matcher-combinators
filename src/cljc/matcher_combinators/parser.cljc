(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers])
  #?(:clj
     (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap
               IPersistentVector IPersistentList IPersistentSet
               LazySeq Repeat Cons Var]
              [java.util UUID Date]
              [java.util.regex Pattern]
              [java.time LocalDate LocalDateTime LocalTime YearMonth])))

#?(:cljs
(extend-protocol
  core/Matcher

  ;; function as predicate
  function
  (match [this actual]
    (core/match-pred this actual))

  ;; equals base types
  nil
  (match [this actual]
    (core/match (matchers/equals this) actual))

  number
  (match [this actual]
    (core/match (matchers/equals this) actual))

  string
  (match [this actual]
    (core/match (matchers/equals this) actual))

  boolean
  (match [this actual]
    (core/match (matchers/equals this) actual))

  Keyword
  (match [this actual]
    (core/match (matchers/equals this) actual))

  UUID
  (match [this actual]
    (core/match (matchers/equals this) actual))

  js/Date
  (match [this actual]
    (core/match (matchers/equals this) actual))

  Var
  (match [this actual]
    (core/match (matchers/equals this) actual))

  ;; equals nested types
  Cons
  (match [this actual]
    (core/match (matchers/equals this) actual))

  Repeat
  (match [this actual]
    (core/match (matchers/equals this) actual))

  default
  (match [this actual]
    (cond
      (satisfies? IMap this)
      (core/match (matchers/embeds this) actual)

      (or (satisfies? ISet this)
          (satisfies? ISequential this))
      (core/match (matchers/equals this) actual)))

  js/RegExp
  (match [this actual]
    (core/match (matchers/regex this) actual))))

#?(:clj (do
(defmacro mimic-matcher [matcher-builder & types]
  `(extend-protocol
    core/Matcher
     ~@(mapcat (fn [t] `(~t
                         (match [this# actual#]
                           (core/match (~matcher-builder this#) actual#)))) types)))

(defmacro mimic-matcher-java-primitives [matcher-builder & type-strings]
  (let [type-pairs (->> type-strings
                        (map symbol)
                        (mapcat (fn [t] `(~t
                                          (match [this# actual#]
                                            (core/match (~matcher-builder this#) actual#))))))]
    `(extend-protocol core/Matcher ~@type-pairs)))

(extend-type clojure.lang.Fn
  core/Matcher
  (match [this actual]
    (core/match-pred this actual)))

(mimic-matcher-java-primitives matchers/equals
                               "[B")

(mimic-matcher matchers/equals
               nil
               java.lang.Class
               Integer
               Short
               Long
               Float
               Double
               String
               Symbol
               Keyword
               Boolean
               UUID
               Date
               LocalDate
               LocalDateTime
               LocalTime
               YearMonth
               Ratio
               BigDecimal
               BigInteger
               BigInt
               Character
               Var)

(mimic-matcher matchers/embeds IPersistentMap)
(mimic-matcher matchers/equals IPersistentVector)
(mimic-matcher matchers/equals IPersistentList)
(mimic-matcher matchers/equals IPersistentSet)
(mimic-matcher matchers/equals Cons)
(mimic-matcher matchers/equals Repeat)
(mimic-matcher matchers/equals LazySeq)
(mimic-matcher matchers/regex Pattern)))
