(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model])
  (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap
            IPersistentVector IPersistentList IPersistentSet
            LazySeq Repeat Cons]
           [java.util UUID Date]
           [java.util.regex Pattern]
           [java.time LocalDate LocalDateTime YearMonth]))

(defmacro mimic-matcher [matcher-builder & types]
  `(extend-protocol
    core/Matcher
     ~@(mapcat (fn [t] `(~t
                         (match [this# actual#]
                           (core/match (~matcher-builder this#) actual#)))) types)))

(extend-type clojure.lang.Fn
  core/Matcher
  (match [this actual]
    (core/match-pred this actual)))

(mimic-matcher matchers/equals
               nil
               Integer
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
               YearMonth
               Ratio
               BigDecimal
               BigInteger
               BigInt
               Character)

(mimic-matcher matchers/embeds IPersistentMap)
(mimic-matcher matchers/equals IPersistentVector)
(mimic-matcher matchers/equals IPersistentList)
(mimic-matcher matchers/equals IPersistentSet)
(mimic-matcher matchers/equals Cons)
(mimic-matcher matchers/equals Repeat)
(mimic-matcher matchers/equals LazySeq)
(mimic-matcher matchers/regex Pattern)
