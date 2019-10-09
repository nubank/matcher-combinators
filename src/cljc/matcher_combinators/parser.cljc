(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.matchers :as matchers])
  #?(:cljs (:import goog.Uri)
     :clj  (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap
                IPersistentVector IPersistentList IPersistentSet
                LazySeq Repeat Cons Var]
               [java.net URI]
               [java.util UUID Date]
               [java.util.regex Pattern]
               [java.time LocalDate LocalDateTime LocalTime YearMonth])))

#?(:cljs
(extend-protocol
  core/Matcher

  ;; function as predicate
  function
  (match [this actual]
    ((dispatch/function-dispatch this) actual))

  ;; equals base types
  nil
  (match [this actual]
    (core/match (dispatch/nil-dispatch this) actual))

  number
  (match [this actual]
    (core/match (dispatch/integer-dispatch this) actual))

  string
  (match [this actual]
    (core/match (dispatch/string-dispatch this) actual))

  boolean
  (match [this actual]
    (core/match (dispatch/boolean-dispatch this) actual))

  Keyword
  (match [this actual]
    (core/match (dispatch/keyword-dispatch this) actual))

  UUID
  (match [this actual]
    (core/match (dispatch/uuid-dispatch this) actual))

  goog.Uri
  (match [this actual]
    (core/match (dispatch/uri-dispatch this) actual))

  js/Date
  (match [this actual]
    (core/match (dispatch/date-dispatch this) actual))

  Var
  (match [this actual]
    (core/match (dispatch/var-dispatch this) actual))

  ;; equals nested types
  Cons
  (match [this actual]
    (core/match (dispatch/cons-dispatch this) actual))

  Repeat
  (match [this actual]
    (core/match (dispatch/repeat-dispatch this) actual))

  default
  (match [this actual]
    (cond
      (satisfies? IMap this)
      (core/match (dispatch/i-persistent-map-dispatch this) actual)

      (or (satisfies? ISet this)
          (satisfies? ISequential this))
      (core/match (dispatch/i-persistent-vector-dispatch this) actual)))

  js/RegExp
  (match [this actual]
    (core/match (dispatch/pattern-dispatch this) actual))))

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
    ((dispatch/function-dispatch this) actual)))

(mimic-matcher-java-primitives matchers/equals
                               "[B")

(mimic-matcher dispatch/nil-dispatch nil)
(mimic-matcher dispatch/class-dispatch java.lang.Class)
(mimic-matcher dispatch/integer-dispatch Integer)
(mimic-matcher dispatch/short-dispatch Short)
(mimic-matcher dispatch/long-dispatch Long)
(mimic-matcher dispatch/float-dispatch Float)
(mimic-matcher dispatch/double-dispatch Double)
(mimic-matcher dispatch/string-dispatch String)
(mimic-matcher dispatch/symbol-dispatch Symbol)
(mimic-matcher dispatch/keyword-dispatch Keyword)
(mimic-matcher dispatch/boolean-dispatch Boolean)
(mimic-matcher dispatch/uuid-dispatch UUID)
(mimic-matcher dispatch/uri-dispatch URI)
(mimic-matcher dispatch/date-dispatch Date)
(mimic-matcher dispatch/local-date-dispatch LocalDate)
(mimic-matcher dispatch/local-date-time-dispatch LocalDateTime)
(mimic-matcher dispatch/local-time-dispatch LocalTime)
(mimic-matcher dispatch/year-month-dispatch YearMonth)
(mimic-matcher dispatch/ratio-dispatch Ratio)
(mimic-matcher dispatch/big-decimal-dispatch BigDecimal)
(mimic-matcher dispatch/big-integer-dispatch BigInteger)
(mimic-matcher dispatch/big-int-dispatch BigInt)
(mimic-matcher dispatch/character-dispatch Character)
(mimic-matcher dispatch/var-dispatch Var)

(mimic-matcher dispatch/i-persistent-map-dispatch IPersistentMap)
(mimic-matcher dispatch/i-persistent-vector-dispatch IPersistentVector)
(mimic-matcher dispatch/i-persistent-list-dispatch IPersistentList)
(mimic-matcher dispatch/i-persistent-list-dispatch IPersistentSet)
(mimic-matcher dispatch/cons-dispatch Cons)
(mimic-matcher dispatch/repeat-dispatch Repeat)
(mimic-matcher dispatch/lazy-seq-dispatch LazySeq)
(mimic-matcher dispatch/pattern-dispatch Pattern)))
