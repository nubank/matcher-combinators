false(ns matcher-combinators.matchers-test
  (:require [midje.sweet :as midje :refer [fact facts => falsey contains just anything future-fact has]]
            [matcher-combinators.midje :refer [match]]
            [matcher-combinators.helpers :as helpers]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.model :as model]
            [matcher-combinators.core :as c])
  (:import [matcher_combinators.model Mismatch Missing InvalidMatcherType]))

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
(defn missing? [actual]
  (instance? Missing actual))
(defn invalid-type? [actual]
  (instance? InvalidMatcherType actual))

(fact "in-any-order using matcher ordering with maximum matchings for diff"
  (c/match (m/in-any-order [a-nested-map b-nested-map])
           [a-nested-map a-nested-map])
  => (just [:mismatch (just [a-nested-map (contains {:id map? :model mismatch?})]
                            :in-any-order)]))

(defn one-mismatch? [mismatch-list]
  (= 1 (count (filter #(or (mismatch? %) (missing? %)) mismatch-list))))

(fact "Ensure that in-any-order always prints the match with the fewest
       number of matchers that don't match"
  (map #(->> %
             (c/match (m/in-any-order [1 2 3 4]))
             second)
       (helpers/permutations [1 2 3 500]))
  => (has every? one-mismatch?))

(facts "Show how input ordering affects diff size (when it ideally shouldn't)"
  (fact "Given a particular input ordering, in-any-order shows the smallest diff"
    (->> [{:a 2} {:b 2}]
         (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
         second
         (map vals))
    => (has every? one-mismatch?))

  (future-fact "in-any-order minimization doesn't find the match ordering that
                leads to the smallest diff, but rather the match ordering that
                leads to the smallest number of immediately passing matchers."
    (->> [{:b 2} {:a 2}]
         (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
         second
         (map vals))
    => (has every? one-mismatch?)))

(fact "Regex matching and mismatching"
  (c/match (m/equals {:one (m/regex #"1")})
           {:one "1"})
  => (just [:match (just {:one "1"})])

  (c/match (m/equals {:one (m/regex #"1")})
           {:one "2"})
  => (just [:mismatch (just {:one mismatch?})])

  (c/match (m/equals {:one (m/regex "1")})
           {:one "1"})
  => (just [:mismatch (just {:one invalid-type?})])

  (c/match (m/equals {:one (m/regex #"1")})
           {:one 2})
  => (just [:mismatch (just {:one invalid-type?})]))
