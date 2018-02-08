false(ns matcher-combinators.matchers-test
  (:require [midje.sweet :as midje :refer [fact facts => falsey contains just anything]]
            [matcher-combinators.midje :refer [match]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.model :as model]
            [matcher-combinators.core :as c])
  (:import [matcher_combinators.model Mismatch]))

(def now (java.time.LocalDateTime/now))
(def an-id-string "67b22046-7e9f-46b2-a3b9-e68618242864")
(def an-id (java.util.UUID/fromString an-id-string))
(def another-id (java.util.UUID/fromString "8f488446-374e-4975-9670-35ca0a633da1"))
(def response-time (java.time.LocalDateTime/now))

(def nested-map
 {:id {:type :user-id
       :value an-id-string}
 :input {:id {:type :user-id
              :value an-id-string}
         :timestamp now
         :trigger "blabla"}
 :model "sampa_v3"
 :output {:sampa-score 123.4M
          :user-id another-id
          :w-alpha -0.123}
 :response-time response-time
 :version "1.33.7"})

(def a-nested-map nested-map)
(def b-nested-map (assoc-in nested-map [:model] "curitiba"))

(defn mismatch? [actual]
  (instance? Mismatch actual))

(fact "in-any-order finds minimal diff"
  (c/match (m/in-any-order [a-nested-map b-nested-map])
           [a-nested-map a-nested-map]) 
  => (just [:mismatch (just [a-nested-map (contains {:id map? :model mismatch?})])]))
