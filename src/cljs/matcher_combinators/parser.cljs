(ns matcher-combinators.parser
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model]))

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
               Keyword
               UUID)

(mimic-matcher matchers/equals APersistentVector)
