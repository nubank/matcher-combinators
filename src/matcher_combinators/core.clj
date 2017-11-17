(ns matcher-combinators.core
  (:require [clojure.set :as set]
            [matcher-combinators.model :as model]
            [midje.checking.checkers.defining :as checkers.defining]))

(defprotocol Matcher
  ""
  (select? [this select-fn candidate] "")
  (match   [this actual]              ""))

(extend-type nil
  Matcher
  (select? [_ _ _] false))

(defn match? [match-result]
  (= :match (first match-result)))

(defn- mismatch? [match-result]
  (= :mismatch (first match-result)))

(defn- value [match-result]
  (second match-result))

(defrecord Value [expected]
  Matcher
  (select? [_this _select-fn candidate]
    (= expected candidate))
  (match [_this actual]
   (cond
     (and (nil? expected)
          (nil? actual))  true
     (nil? actual)        [:mismatch (model/->Missing expected)]
     (= expected actual)  [:match actual]
     :else                [:mismatch (model/->Mismatch expected actual)])))

(defn equals-value [expected]
  (->Value expected))

(declare derive-matcher)
(defn- allow-unexpected? [matcher-type]
  (= matcher-type :embeds))

(defn- compare-maps [expected actual unexpected-handler matcher-type]
  (let [entry-results      (map (fn [[key value-or-matcher]]
                                  [key (match (derive-matcher value-or-matcher matcher-type)
                                              (get actual key))])
                                expected)
        unexpected-entries (keep (fn [[key val]]
                                   (when-not (find expected key)
                                     [key (unexpected-handler val)]))
                                 actual)]
    (if (and (every? (comp match? value) entry-results)
             (or (allow-unexpected? matcher-type) (empty? unexpected-entries)))
      [:match actual]
      [:mismatch (->> entry-results
                      (map (fn [[key match-result]] [key (value match-result)]))
                      (concat unexpected-entries)
                      (into actual))])))

(defn- match-map [expected actual unexpected-handler matcher-type]
  (if-not (map? actual)
    [:mismatch (model/->Mismatch expected actual)]
    (compare-maps expected actual unexpected-handler matcher-type)))

(defrecord ContainsMap [expected]
  Matcher
  (select? [this select-fn candidate]
    (let [selected-matcher (select-fn expected)
          selected-value   (select-fn candidate)]
      (select? selected-matcher select-fn selected-value)))
  (match [_this actual]
    (match-map expected actual identity :contains)))

(defn contains-map [expected]
  (->ContainsMap expected))

(defrecord EqualsMap [expected]
  Matcher
  (select? [_this select-fn candidate]
    (let [selected-matcher (select-fn expected)
          selected-value   (select-fn candidate)]
      (select? selected-matcher select-fn selected-value)))
  (match [_this actual]
    (match-map expected actual model/->Unexpected :equals)))

(defn equals-map [expected]
  (->EqualsMap expected))

(defn- match-embeds-map [expected actual unexpected-handler]
  (if-not (map? actual)
    [:mismatch (model/->Mismatch expected actual)]
    (compare-maps expected actual unexpected-handler :embeds)))

(defrecord EmbedsMap [expected]
  Matcher
  (match [_this actual]
    (match-embeds-map expected actual identity)))

(defn embeds-map [expected]
  (->EmbedsMap expected))

(defn sequence-match [expected actual subseq?]
  (if-not (sequential? actual)
      [:mismatch (model/->Mismatch expected actual)]
      ;; TODO PLM: if we want to pass down matcher types between maps/vectors,
      ;; the `:equals` needs to be dynamically determined
      (let [matcher-fns     (concat (map #(partial match (derive-matcher % :equals)) expected)
                                    (repeat (fn [extra-element]
                                              [:mismatch (model/->Unexpected extra-element)])))
            actual-elements (concat actual (repeat nil))
            match-results'  (map (fn [match-fn actual-element] (match-fn actual-element))
                                 matcher-fns actual-elements)
            match-size      (if subseq?
                              (count expected)
                              (max (count actual) (count expected)))
            match-results   (take match-size match-results')]
        (if (some mismatch? match-results)
          [:mismatch (map value match-results)]
          [:match actual]))))

(defrecord EqualsSequence [expected]
  Matcher
  (match [_this actual]
    (sequence-match expected actual false)))

;; This is just like midje's `just`
(defn equals-sequence [expected]
  (->EqualsSequence expected))

(defn- find-first [pred coll]
  (->> coll (filter pred) first))

(defn- permutations
  "Lazy seq of all permutations of a seq"
  [coll]
  (for [i (range 0 (count coll))]
    (lazy-cat (drop i coll) (take i coll))))

(defn- matches-in-any-order? [matchers elements subset?]
  (if (empty? matchers)
    (or subset? (empty? elements))
    (let [[first-element & rest-elements] elements
          matching-matcher (find-first #(match? (match (derive-matcher % :equals) first-element)) matchers)]
      (if (nil? matching-matcher)
        false
        (recur (remove #{matching-matcher} matchers) rest-elements subset?)))))

(defn- match-all-permutations [match-apply-fn matchers elements subset?]
  (find-first (fn [matchers] (match-apply-fn matchers elements subset?))
              (permutations matchers)))

(defn- match-any-order [expected actual subset?]
  (cond
    (not (sequential? actual))
    [:mismatch (model/->Mismatch expected actual)]

    (and (not subset?) (not (= (count expected) (count actual))))
    ;; for size mismatch, is there a more detailed mismatch model to use?
    [:mismatch (model/->Mismatch expected actual)]

    (match-all-permutations matches-in-any-order? expected actual subset?)
    [:match actual]

    :else
    [:mismatch (model/->Mismatch expected actual)]))

(defrecord AllOrNothingInAnyOrder [expected]
  Matcher
  (match [_this actual]
    (match-any-order expected actual false)))

(defn- base-selecting-match [matchers matching? matched-elements]
  (if (empty? matchers)
    [matching? (reverse matched-elements)]
    [:mismatch (concat (reverse matched-elements)
                       (map #(value (match % nil)) matchers))]))

(defn- selecting-match [select-fn all-matchers all-elements]
  (loop [elements         all-elements
         matchers         all-matchers
         matching?        :match
         matched-elements []]
    (if (empty? elements)
      (base-selecting-match matchers matching? matched-elements)
      (let [[element & rest-elements]  elements
            selected-matcher           (find-first #(select? % select-fn element) matchers)
            [match-result match-value] (if selected-matcher
                                         (match selected-matcher element)
                                         [:mismatch (model/->Unexpected element)])]
        (recur rest-elements
               (remove #{selected-matcher} matchers)
               (if (= :mismatch matching?) :mismatch match-result)
               (cons match-value matched-elements))))))

(defrecord SelectingInAnyOrder [select-fn expected]
  Matcher
  (match [_this actual]
    (if-not (sequential? actual)
      [:mismatch (model/->Mismatch expected actual)]
      (selecting-match select-fn expected actual))))

;; This is just like midje's `just :in-any-order`
(defn in-any-order
  ([expected]
   (->AllOrNothingInAnyOrder expected))
  ([select-fn expected]
   (->SelectingInAnyOrder select-fn expected)))

(defrecord SubSeq [expected]
  Matcher
  (match [_this actual]
    (sequence-match expected actual true)))

;; This is just like midje's `contains`
(defn match-subseq [expected]
  (->SubSeq expected))

(defrecord SubSet [expected]
  Matcher
  (match [_this actual]
    (match-any-order expected actual true)))

;; This is just like midje's `contains :in-any-order :gaps-ok`
(defn match-subset [expected]
   (->SubSet expected))

(defrecord Predicate [func form]
  Matcher
  (match [_this actual]
    (if (func actual)
      [:match actual]
      [:mismatch (model/->FailedPredicate form actual)])))

(defmacro pred->matcher
  "turns a normal predicate function into a matcher"
  [pred & args]
  (if (empty? args)
    `(->Predicate ~pred '~pred)
    `(->Predicate (~pred ~@args) '(~pred ~@args))))

(defn- derive-matcher [value-or-matcher parent-matcher-type]
  (cond
    (satisfies? Matcher value-or-matcher)
    value-or-matcher

    ;; TODO PLM: potentially needs to be refined to handle different type of
    ;; sequence matchers
    (vector? value-or-matcher)
    (equals-sequence value-or-matcher)

    (checkers.defining/checker? value-or-matcher)
    (pred->matcher value-or-matcher)

    (= :equals parent-matcher-type)
    (equals-map value-or-matcher)

    (= :embeds parent-matcher-type)
    (embeds-map value-or-matcher)

    (= :contains parent-matcher-type)
    (contains-map value-or-matcher)

    :else
    (throw (ex-info "Unable to derive matcher"
                    {:input value-or-matcher
                     :parent-matcher parent-matcher-type}))))
