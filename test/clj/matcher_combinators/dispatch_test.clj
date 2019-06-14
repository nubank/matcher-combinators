(ns matcher-combinators.dispatch_test
  (:require [midje.sweet :as midje :refer [fact facts => falsey]]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.standalone :as s]))

(fact
  (s/match? -1 1) => false

  (dispatch/wrap-match-with
    {java.lang.Long (fn [x] (core/->Value (abs x)))}
    (s/match? -1 1))
  => true)


