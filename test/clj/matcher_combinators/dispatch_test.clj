(ns matcher-combinators.dispatch_test
  (:require [midje.sweet :as midje :refer [fact facts => falsey]]
            [matcher-combinators.core :as core]
            [matcher-combinators.midje :refer [match match-with match-equals match-roughly]]
            [matcher-combinators.result :as result]
            [matcher-combinators.model :as model]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.standalone :as s]))

(defn abs [x]
  (if (neg? x)
    (* -1 x)
    x))

(defrecord AbsValue [expected]
  core/Matcher
  (match [_this actual]
    (cond
      (= ::missing actual) {::result/type   :mismatch
                            ::result/value  (model/->Missing expected)
                            ::result/weight 1}
      (= (abs expected)
         (abs actual))     {::result/type   :match
                            ::result/value  actual
                            ::result/weight 0}
      :else                {::result/type   :mismatch
                            ::result/value  (model/->Mismatch expected actual)
                            ::result/weight 1})))

(def match-abs (match-with {java.lang.Long ->AbsValue}))

(facts "match-with checker behavior"
  (s/match? -1 1) => false

  (fact "low-level invocation"
    (dispatch/wrap-match-with
     {java.lang.Long ->AbsValue}
     (s/match? -1 1))
    => true)

  (fact "using 2-arg match-with"
    1 => (match-with {java.lang.Long ->AbsValue} -1)
    -1 => (match-with {java.lang.Long ->AbsValue} 1))
  (fact "binding 1-arg match-with to new checker"
    1 => (match-abs -1)
    -1 => (match-abs 1)))

(facts "match-equals"
  (fact "normal loose matching passes"
    {:a 1 :b 3 :c 1} => (match {:a 1 :b odd?}))
  (fact "match-equals is more strict"
    {:a 1 :b 3 :c 1} =not=> (match-equals {:a 1 :b odd?})
    {:a 1 :b 3} => (match-equals {:a 1 :b odd?}))
  (let [payload {:a {:b {:c 1}
                     :d {:e {:inner-e {:x 1 :y 2}}
                         :f 5
                         :g 17}}}]
    (fact "nested maps inside of an `embeds` of a match-equals are treated as equals"
      payload
      =not=> (match-equals {:a {:b {:c 1}
                                :d (m/embeds {:e {:inner-e {:x 1}}})}})
      payload
      => (match-equals {:a {:b {:c 1}
                            :d (m/embeds {:e {:inner-e {:x 1 :y 2}}})}}))))

(fact "match-roughly"
  {:a 1 :b 3.05} => (match-roughly 0.1
                                   {:a 1 :b 3.0})
  {:a 1 :b 3.05} =not=> (match-roughly 0.001
                                       {:a 1 :b 3.0}))

(defn greater-than-matcher [expected-long]
  (matcher-combinators.core/->PredMatcher
   (fn [actual] (> actual expected-long))))

(fact "example from docstring"
  5 => (match-with {java.lang.Long greater-than-matcher}
                   4))
