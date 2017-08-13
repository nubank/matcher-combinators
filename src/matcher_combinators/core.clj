(ns matcher-combinators.core
  (:require [clojure.set :as set]))

(defrecord Mismatch [expected actual])
(defrecord Missing  [expected])
(defrecord Unexpected [actual])

(defn- match? [match-result]
  (= :match (first match-result)))

(defn- mismatch? [match-result]
  (= :mismatch (first match-result)))

(defn- value [match-result]
  (second match-result))

(defn equals-value [expected]
  (fn [actual]
    (cond (and (nil? expected) (nil? actual)) true
          (nil? actual)                       [:mismatch (->Missing expected)]
          (= expected actual)                 [:match actual]
          :else                               [:mismatch (->Mismatch expected actual)])))

(defn- check-contains-map [expected actual])

(defn contains-map [expected]
  (fn [actual]
    (if-not (map? actual)
      [:mismatch (->Mismatch expected actual)]
      (let [entry-results (map (fn [[key value-matcher]] [key (value-matcher (get actual key))]) expected)]
        (if (every? (fn [[_ match-result]] (match? match-result)) entry-results)
          [:match actual]
          [:mismatch (->> entry-results
                          (map (fn [[key match-result]] [key (value match-result)]))
                          (into actual))])))))

(defn equals-map [expected]
  (fn [actual]
    (let [entry-results      (map (fn [[key value-matcher]] [key (value-matcher (get actual key))]) expected)
          unexpected-entries (keep (fn [[key value]] (when-not (find expected key) [key (->Unexpected value)])) actual)]
      (if (and (every? (fn [[_ match-result]] (match? match-result)) entry-results)
               (empty? unexpected-entries))
        [:match actual]
        [:mismatch (->> entry-results
                        (map (fn [[key match-result]] [key (value match-result)]))
                        (concat unexpected-entries)
                        (into actual))]))))

(defn equals-sequence [expected]
  (fn [actual]
    (let [expected-matchers (concat expected (repeat (fn [extra-element] [:mismatch (->Unexpected extra-element)])))
          match-results (map (fn [matcher actual-element] (matcher actual-element)) expected-matchers actual)]
      (if (every? match? match-results)
        [:match actual]
        [:mismatch (map value match-results)]))))
