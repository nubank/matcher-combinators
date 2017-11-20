(ns matcher-combinators.midje
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.parser]
            [matcher-combinators.printer :as printer]
            [midje.checking.core :as checking]
            [midje.util.exceptions :as exception]
            [midje.checking.checkers.defining :as checkers.defining])
  (:import [matcher_combinators.core Matcher]))

(defn- check-match [matcher actual]
  (if (exception/captured-throwable? actual)
    (checking/as-data-laden-falsehood {:notes [(exception/friendly-stacktrace actual)]})
    (let [result (core/match matcher actual)]
      (if (core/match? result)
        true
        (checking/as-data-laden-falsehood {:notes [(printer/print result)]})))))


(defrecord Checker [matcher]
  clojure.lang.IFn
    (invoke [_this actual]
      (check-match matcher actual))

  Matcher
  (match [_this actual]
    (core/match matcher actual)))

(defn equals-map [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/equals-map expected))))

(defn embeds-map [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/embeds-map expected))))

(defn contains-map [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/contains-map expected))))

(defn equals-sequence [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/equals-sequence expected))))

(defn in-any-order [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/in-any-order expected))))

(defn match-subseq [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/match-subseq expected))))

(defn match-subset [expected]
  (checkers.defining/as-checker
    (->Checker
      (core/match-subset expected))))
