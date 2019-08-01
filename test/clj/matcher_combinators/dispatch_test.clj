(ns matcher-combinators.dispatch_test
  (:require [midje.sweet :as midje :refer [fact facts => falsey]]
            [matcher-combinators.core :as core]
            [matcher-combinators.midje :refer [match-with]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.standalone :as s]))

(defn abs [x]
  (if (neg? x)
    (* -1 x)
    x))
; (clojure.pprint/pprint (macroexpand `(match-with {clojure.lang.IPersistentMap m/equals} {:a 1 :b odd?})))

(def x {clojure.lang.IPersistentMap m/equals})
(def match-strict (match-with {clojure.lang.IPersistentMap m/equals}))
(fact
  (s/match? -1 1) => false

  (dispatch/wrap-match-with
    {java.lang.Long (fn [x] (core/->Value (abs x)))}
    (s/match? -1 1))
  => true

  (dispatch/wrap-match-with
   {clojure.lang.IPersistentMap m/equals}
    (s/match? {:a 1 :b odd?} {:a 1 :b 2})) => true

  {:a 1 :b 3} => (match-with
                   {clojure.lang.IPersistentMap m/equals}
                   {:a 1 :b odd?})

  {:a 1 :b 3} => (match-strict {:a 1 :b odd?})
  )


