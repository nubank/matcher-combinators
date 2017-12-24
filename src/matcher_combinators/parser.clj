(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model])
  (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap IPersistentVector]
           [java.util UUID Date]
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
    (if (this actual)
      [:match actual]
      [:mismatch (model/->FailedPredicate (str this) actual)])))

(mimic-matcher matchers/equals-value
               Long
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

(mimic-matcher matchers/contains-map IPersistentMap)
(mimic-matcher matchers/equals-seq IPersistentVector)