(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model])
  (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap IPersistentVector IPersistentList IPersistentSet]
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

(defn- map-dispatch [expected]
  (core/->EmbedsMap expected))

(defn- number-dispatch [expected]
  (core/->Value expected))

(defn- seq-dispatch [expected]
  (core/->EqualsSeq expected))

(defn- set-dispatch [expected]
  (core/->SetEquals expected false))

(def type-map
  {:number #'number-dispatch
   :map    #'map-dispatch
   :seq    #'seq-dispatch
   :set    #'set-dispatch})

(mimic-matcher number-dispatch
               Long
               Double
               BigDecimal
               BigInteger
               BigInt)

(mimic-matcher matchers/equals
               nil
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
               Character)

(mimic-matcher map-dispatch IPersistentMap)
(mimic-matcher seq-dispatch IPersistentVector)
(mimic-matcher seq-dispatch IPersistentList)
(mimic-matcher set-dispatch IPersistentSet)
