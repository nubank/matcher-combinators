(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers])
  #?(:cljs (:import goog.Uri)
     :clj  (:import (clojure.lang IPersistentMap)
                    (java.util.regex Pattern))))

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
(defmacro mimic-matcher [matcher t]
  `(extend-type ~t
     core/Matcher
     (~'-matcher-for
      ([this#] ~matcher)
      ([this# t->m#] (matchers/lookup-matcher this# t->m#)))
     (~'-match [this# actual#]
      (core/match (~matcher this#) actual#))))

;; default for most objects
(mimic-matcher matchers/equals Object)

;; nil is a special case
(mimic-matcher matchers/equals nil)

;; regex
(mimic-matcher matchers/regex Pattern)

;; collections
(mimic-matcher matchers/embeds IPersistentMap)

;; functions are special, too
(extend-type clojure.lang.Fn
  core/Matcher
  (-matcher-for
    ([this] (matchers/pred this))
    ([this t->m] (matchers/pred this)))
  (-match [this actual]
    (core/match (matchers/pred this) actual)))))
