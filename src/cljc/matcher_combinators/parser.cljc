(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers])
  #?(:cljs (:import goog.Uri)
     :clj  (:import [clojure.lang ArraySeq Keyword Symbol Ratio BigInt IPersistentMap
                PersistentVector$ChunkedSeq IPersistentVector IPersistentList IPersistentSet
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
  (-match [this actual]
    (core/match (matchers/pred this) actual))

  ;; equals base types
  nil
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  number
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  string
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  boolean
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  Keyword
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  Symbol
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  UUID
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  goog.Uri
  (-match [this actual]
    (core/match (matchers/cljs-uri this) actual))

  js/Date
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  Var
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  ;; equals nested types
  Cons
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  Repeat
  (-match [this actual]
    (core/match (matchers/equals this) actual))

  default
  (-match [this actual]
    (cond
      (satisfies? IMap this)
      (core/match (matchers/embeds this) actual)

      (or (satisfies? ISet this)
          (satisfies? ISequential this))
      (core/match (matchers/equals this) actual)))

  js/RegExp
  (-match [this actual]
    (core/match (matchers/regex this) actual))))

#?(:clj (do
(defmacro mimic-matcher [matcher-builder & types]
  `(extend-protocol
    core/Matcher
     ~@(mapcat (fn [t] `(~t
                         (~'-matcher-for
                          ([this#]
                           (~matcher-builder this#))
                          ([this# t->m#]
                           (let [m# (matchers/lookup-matcher ~t t->m#)]
                             (m# this#))))
                         (~'-match [this# actual#]
                          (core/match (~matcher-builder this#) actual#))))
         types)))

(defmacro mimic-matcher-java-primitives [matcher-builder & type-strings]
  (let [type-pairs (->> type-strings
                        (map symbol)
                        (mapcat (fn [t] `(~t
                                          (~'-matcher-for
                                           ([this#] (~matcher-builder this#))
                                           ([this# t->m#]
                                            (let [m# (matchers/lookup-matcher ~t t->m#)]
                                              (m# this#))))
                                          (~'-match [this# actual#]
                                            (core/match (~matcher-builder this#) actual#))))))]
    `(extend-protocol core/Matcher ~@type-pairs)))

(mimic-matcher-java-primitives matchers/equals
                               "[B")

(extend-type clojure.lang.Fn
  core/Matcher
  (-matcher-for
    ([this] (matchers/pred this))
    ([this t->m] (matchers/pred this)))
  (-match [this actual]
    (core/match (matchers/pred this) actual)))

;; scalars
(mimic-matcher matchers/equals Object)
(mimic-matcher matchers/equals java.lang.Class)
(mimic-matcher matchers/equals nil)
(mimic-matcher matchers/equals Integer)
(mimic-matcher matchers/equals Short)
(mimic-matcher matchers/equals Long)
(mimic-matcher matchers/equals Float)
(mimic-matcher matchers/equals Double)
(mimic-matcher matchers/equals String)
(mimic-matcher matchers/equals Symbol)
(mimic-matcher matchers/equals Keyword)
(mimic-matcher matchers/equals Boolean)
(mimic-matcher matchers/equals UUID)
(mimic-matcher matchers/equals URI)
(mimic-matcher matchers/equals Date)
(mimic-matcher matchers/equals LocalDate)
(mimic-matcher matchers/equals LocalDateTime)
(mimic-matcher matchers/equals LocalTime)
(mimic-matcher matchers/equals YearMonth)
(mimic-matcher matchers/equals Ratio)
(mimic-matcher matchers/equals BigDecimal)
(mimic-matcher matchers/equals BigInteger)
(mimic-matcher matchers/equals BigInt)
(mimic-matcher matchers/equals Character)
(mimic-matcher matchers/equals Var)
(mimic-matcher matchers/regex Pattern)

;; collections
(mimic-matcher matchers/embeds IPersistentMap)
(mimic-matcher matchers/equals IPersistentVector)
(mimic-matcher matchers/equals PersistentVector$ChunkedSeq)
(mimic-matcher matchers/equals IPersistentList)
(mimic-matcher matchers/equals IPersistentSet)
(mimic-matcher matchers/equals Cons)
(mimic-matcher matchers/equals Repeat)
(mimic-matcher matchers/equals LazySeq)
(mimic-matcher matchers/equals ArraySeq)))
