(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core])
  (:import [clojure.lang Keyword Symbol Ratio BigInt]
           [java.util UUID Date]
           [java.time LocalDate LocalDateTime YearMonth]))

(defmacro mimic-matcher [matcher-builder & types]
  `(extend-protocol
     core/Matcher
     ~@(mapcat (fn [t] `(~t
                          (match [this# actual#]
                                (core/match (~matcher-builder this#) actual#)))) types)))

(mimic-matcher core/equals-value
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
