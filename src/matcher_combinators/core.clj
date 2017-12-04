(ns matcher-combinators.core
  (:require [clojure.set :as set]
            [matcher-combinators.helpers :as helpers]
            [matcher-combinators.model :as model]))

(defprotocol Matcher
  "For matching expected and actual values, providing helpful mismatch info on
  unsucessful matches"
  (select? [this select-fn candidate]
           "useful for anchoring specific substructures for `in-any-order` matchers")
  (match   [this actual]
           "determine if a concrete `actual` value satisfies this matcher"))

(extend-type nil
  Matcher
  (select? [_ _ _] false))

(defn match? [match-result]
  (= :match (first match-result)))

(defn- mismatch? [match-result]
  (= :mismatch (first match-result)))

(defn matcher? [x]
  (satisfies? Matcher x))

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

(defn equals-value
  "Matcher that will match when the given value is exactly the same as the
  `expected`."
  [expected]
  (->Value expected))

(defrecord Predicate [func form]
  Matcher
  (match [_this actual]
    (if (func actual)
      [:match actual]
      [:mismatch (model/->FailedPredicate form actual)])))

(defmacro pred->matcher
  "Turns a normal predicate function into a matcher.
  Preserves syntactical info when showing mismatch output."
  [pred & args]
  (if (empty? args)
    `(->Predicate ~pred '~pred)
    `(->Predicate (~pred ~@args) '(~pred ~@args))))

(defn- derive-matcher [matcher-or-pred]
  (cond
    (matcher? matcher-or-pred)
    matcher-or-pred

    ;; Ideally we would capture the syntactical form of the pred, because
    ;; currently anonymous function info gets lost. Note that doing so would
    ;; require macro magic, so a work-around is to use `pred->matcher`.
    (helpers/extended-fn? matcher-or-pred)
    (->Predicate matcher-or-pred (str matcher-or-pred))

    :else
    (throw (ex-info "Unable to derive matcher" {:input matcher-or-pred}))))

(defn- compare-maps [expected actual unexpected-handler allow-unexpected?]
  (let [entry-results      (map (fn [[key matcher-or-pred]]
                                  [key (match (derive-matcher matcher-or-pred)
                                              (get actual key))])
                                expected)
        unexpected-entries (keep (fn [[key val]]
                                   (when-not (find expected key)
                                     [key (unexpected-handler val)]))
                                 actual)]
    (if (and (every? (comp match? value) entry-results)
             (or allow-unexpected? (empty? unexpected-entries)))
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
    (match-map expected actual identity true)))

(defn contains-map
  "Matcher that will match when the map, and any nested maps, contain some of
  the same key/values as the `expected` map."
  [expected]
  (->ContainsMap expected))

(defrecord EqualsMap [expected]
  Matcher
  (select? [_this select-fn candidate]
    (let [selected-matcher (select-fn expected)
          selected-value   (select-fn candidate)]
      (select? selected-matcher select-fn selected-value)))
  (match [_this actual]
    (match-map expected actual model/->Unexpected false)))

(defn equals-map
  "Matcher that will match when the given map is exactly the same as the
  `expected` map."
  [expected]
  (assert (map? expected))
  (->EqualsMap expected))

(defn- sequence-match [expected actual subseq?]
  (if-not (sequential? actual)
      [:mismatch (model/->Mismatch expected actual)]
      ;; TODO PLM: if we want to pass down matcher types between maps/vectors,
      ;; the `:equals` needs to be dynamically determined
      (let [matcher-fns     (concat (map #(partial match (derive-matcher %)) expected)
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

(defn equals-seq
  "Matcher that will match when the given list is exactly the same as the
  `expected`.

  Similar to midje's `(just expected)`"
  [expected]
  (assert (vector? expected))
  (->EqualsSequence expected))

(defn- matches-in-any-order? [matchers elements subset?]
  (if (empty? matchers)
    (or subset? (empty? elements))
    (let [[first-element & rest-elements] elements
          matching-matcher (helpers/find-first
                             #(match? (match (derive-matcher %) first-element))
                             matchers)]
      (if (nil? matching-matcher)
        false
        (recur (remove #{matching-matcher} matchers) rest-elements subset?)))))

(defn- match-all-permutations [matchers elements subset?]
  (helpers/find-first (fn [matchers] (matches-in-any-order? matchers elements subset?))
              (helpers/permutations matchers)))

(defn- match-any-order [expected actual subset?]
  (cond
    (not (sequential? actual))
    [:mismatch (model/->Mismatch expected actual)]

    (and (not subset?) (not (= (count expected) (count actual))))
    ;; for size mismatch, is there a more detailed mismatch model to use?
    [:mismatch (model/->Mismatch expected actual)]

    (match-all-permutations expected actual subset?)
    [:match actual]

    :else
    [:mismatch (model/->Mismatch expected actual)]))

(defrecord InAnyOrder [expected]
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
            selected-matcher           (helpers/find-first #(select? % select-fn element) matchers)
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

(defn in-any-order
  "Matcher that will match when the given a list that is the same as the
  `expected` list but with elements in a different order.

  `select-fn`: optional argument used to anchoring specific substructures to
               clarify mismatch output

  Similar to Midje's `(just expected :in-any-order)`"
  ([expected]
   (->InAnyOrder expected))
  ([select-fn expected]
   (->SelectingInAnyOrder select-fn expected)))

(defrecord SubSeq [expected]
  Matcher
  (match [_this actual]
    (sequence-match expected actual true)))

(defn sublist
  "Matcher that will match when provided a (ordered) prefix of the `expected`
  list.

  Similar to Midje's `(contains expected)`"
  [expected]
  (assert (vector? expected))
  (->SubSeq expected))

(defrecord SubSet [expected]
  Matcher
  (match [_this actual]
    (match-any-order expected actual true)))

(defn subset
  "Order-agnostic matcher that will match when provided a subset of the
  `expected` list.

  Similar to Midje's `(contains expected :in-any-order :gaps-ok)`"
  [expected]
  (assert (vector? expected))
  (->SubSet expected))
