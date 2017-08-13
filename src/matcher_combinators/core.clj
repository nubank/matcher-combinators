(ns matcher-combinators.core
  (:require [clojure.set :as set]))

(defprotocol Matcher
  (match [this actual]))

(defrecord Mismatch [expected actual])
(defrecord Missing  [expected])
(defrecord Unexpected [actual])

(defn- match? [match-result]
  (= :match (first match-result)))

(defn- mismatch? [match-result]
  (= :mismatch (first match-result)))

(defn- value [match-result]
  (second match-result))


(defrecord Value [expected]
  Matcher
  (match [_this actual]
   (cond (and (nil? expected) (nil? actual)) true
         (nil? actual) [:mismatch (->Missing expected)]
         (= expected actual) [:match actual]
         :else [:mismatch (->Mismatch expected actual)])))

(defn equals-value [expected]
  (->Value expected))

(defrecord ContainsMap [expected]
  Matcher
  (match [_this actual]
    (if-not (map? actual)
      [:mismatch (->Mismatch expected actual)]
      (let [entry-results (map (fn [[key value-matcher]] [key (match value-matcher (get actual key))]) expected)]
        (if (every? (fn [[_ match-result]] (match? match-result)) entry-results)
          [:match actual]
          [:mismatch (->> entry-results
                          (map (fn [[key match-result]] [key (value match-result)]))
                          (into actual))])))))

(defn contains-map [expected]
  (->ContainsMap expected))

(defrecord EqualsMap [expected]
  Matcher
  (match [_this actual]
    (let [entry-results      (map (fn [[key value-matcher]] [key (match value-matcher (get actual key))]) expected)
          unexpected-entries (keep (fn [[key value]] (when-not (find expected key) [key (->Unexpected value)])) actual)]
      (if (and (every? (fn [[_ match-result]] (match? match-result)) entry-results)
               (empty? unexpected-entries))
        [:match actual]
        [:mismatch (->> entry-results
                        (map (fn [[key match-result]] [key (value match-result)]))
                        (concat unexpected-entries)
                        (into actual))]))))

(defn equals-map [expected]
  (->EqualsMap expected))

(defrecord EqualsSequence [expected]
  Matcher
  (match [_this actual]
    (let [expected-matchers (concat (map #(partial match %) expected)
                                    (repeat (fn [extra-element] [:mismatch (->Unexpected extra-element)])))
          match-results     (map (fn [match-fn actual-element] (match-fn actual-element)) expected-matchers actual)]
      (if (every? match? match-results)
        [:match actual]
        [:mismatch (map value match-results)]))))

(defn equals-sequence [expected]
  (->EqualsSequence expected))

