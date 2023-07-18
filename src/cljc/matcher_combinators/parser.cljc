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
  (-matcher-for
    ([this] (matchers/pred this))
    ([this _t->m] (matchers/pred this)))

  ;; equals base types
  nil
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  number
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  string
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  boolean
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  Keyword
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  Symbol
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  UUID
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  goog.Uri
  (-match [this actual]
    (core/match (matchers/cljs-uri this) actual))
  (-matcher-for
    ([_this] matchers/cljs-uri)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  js/Date
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  Var
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  ;; equals nested types
  Cons
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  Repeat
  (-match [this actual]
    (core/match (matchers/equals this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))

  default
  (-match [this actual]
    (cond
      (satisfies? IMap this)
      (core/match (matchers/embeds this) actual)

      (or (satisfies? ISet this)
          (satisfies? ISequential this))
      (core/match (matchers/equals this) actual)))
  (-matcher-for
    ([this] (if (satisfies? IMap this)
              matchers/embeds
              matchers/equals))
    ([this t->m] (matchers/lookup-matcher this t->m)))

  js/RegExp
  (-match [this actual]
    (core/match (matchers/regex this) actual))
  (-matcher-for
    ([_this] matchers/equals)
    ([this t->m] (matchers/lookup-matcher this t->m)))))

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
    ([this t->m] (matchers/lookup-matcher this t->m)))
  (-match [this actual]
    (core/match (matchers/pred this) actual)))))
