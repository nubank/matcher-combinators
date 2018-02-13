(ns matcher-combinators.core
  (:require [matcher-combinators.helpers :as helpers]
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
  (match [_ actual]
    (if (nil? actual)
      [:match nil]
      [:mismatch (model/->Mismatch nil actual)]))
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
          (nil? actual))  [:match nil]
     (= ::missing actual) [:mismatch (model/->Missing expected)]
     (= expected actual)  [:match actual]
     :else                [:mismatch (model/->Mismatch expected actual)])))

(defn- validate-input
  ([expected actual pred matcher-name type]
   (validate-input expected actual pred pred matcher-name type))
  ([expected actual expected-pred actual-pred matcher-name type]
   (cond
     (not (expected-pred expected))
     [:mismatch (model/->InvalidMatcherType
                  (str "provided: " expected)
                  (str matcher-name
                       " should be called with 'expected' argument of type: "
                       type))]

     (not (actual-pred actual))
     [:mismatch (model/->Mismatch expected actual)]

     :else
     nil)))

(defrecord InvalidType [provided matcher-name type-msg]
  Matcher
  (match [_this _actual]
    [:mismatch (model/->InvalidMatcherType
                 (str "provided: " provided)
                 (str matcher-name
                      " should be called with 'expected' argument of type: "
                      type-msg))]))

(defn- compare-maps [expected actual unexpected-handler allow-unexpected?]
  (let [entry-results      (map (fn [[key matcher]]
                                  [key (match matcher (get actual key ::missing))])
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

(defrecord ContainsMap [expected]
  Matcher
  (select? [this select-fn candidate]
    (let [selected-matcher (select-fn expected)
          selected-value   (select-fn candidate)]
      (select? selected-matcher select-fn selected-value)))
  (match [_this actual]
    (if-let [validation-issue (validate-input expected actual map? 'contains 'map)]
      validation-issue
      (compare-maps expected actual identity true))))

(defrecord EqualsMap [expected]
  Matcher
  (select? [_this select-fn candidate]
    (let [selected-matcher (select-fn expected)
          selected-value   (select-fn candidate)]
      (select? selected-matcher select-fn selected-value)))
  (match [_this actual]
    (if-let [validation-issue (validate-input expected actual map? 'equals 'map)]
      validation-issue
      (compare-maps expected actual model/->Unexpected false))))

(defn- sequence-match [expected actual subseq?]
  (if-not (sequential? actual)
      [:mismatch (model/->Mismatch expected actual)]
      (let [matcher-fns     (concat (map #(partial match %) expected)
                                    (repeat (fn [extra-element]
                                              [:mismatch (model/->Unexpected extra-element)])))
            actual-elements (concat actual (repeat ::missing))
            match-results'  (map (fn [match-fn actual-element] (match-fn actual-element))
                                 matcher-fns actual-elements)
            match-size      (if subseq?
                              (count expected)
                              (max (count actual) (count expected)))
            match-results   (take match-size match-results')]
        (if (some mismatch? match-results)
          [:mismatch (into (empty actual) (map value match-results))]
          [:match actual]))))

(defrecord EqualsSeq [expected]
  Matcher
  (match [_this actual]
    (if-let [validation-issue (validate-input
                                expected actual sequential? 'equals 'sequential)]
      validation-issue
      (sequence-match expected actual false))))

(defn- matches-in-any-order? [unmatched elements subset? matching]
  (if (empty? unmatched)
    {:matched? (or subset? (empty? elements))
     :unmatched []
     :matched   matching}
    (let [[elem & rest-elements] elements
          matching-matcher       (helpers/find-first
                                   #(match? (match % elem))
                                   unmatched)]
      (if (nil? matching-matcher)
        {:matched?  false
         :unmatched unmatched
         :matched   matching}
        (recur (helpers/remove-first #{matching-matcher} unmatched)
               rest-elements
               subset?
               (conj matching matching-matcher))))))

(defn- matched-or-best-matchers [matchers subset?]
  (fn [best elements]
    (let [{:keys [matched?
                  unmatched
                  matched]} (matches-in-any-order? matchers elements subset? [])]
      (cond
        matched?                    (reduced true)
        (> (count matched)
           (count (:matched best))) {:matched   matched
                                     :unmatched unmatched
                                     :elements  elements}
        :else                       best))))

(defn- match-all-permutations [matchers elements subset?]
  (let [elem-permutations (helpers/permutations elements)
        find-best-match   (matched-or-best-matchers matchers subset?)
        result            (reduce find-best-match
                                  {:matched   []
                                   :unmatched matchers
                                   :elements  elements}
                                  elem-permutations)]
    (if (boolean? result)
      [:match elements]
      (match (->EqualsSeq (concat (:matched result)
                                  (:unmatched result)))
             (:elements result)))))

(defn- incorrect-matcher->element-count?
  [subset? matcher-count element-count]
  (if subset?
    (> matcher-count element-count)
    (not (= matcher-count element-count))))

(defn- match-any-order [expected actual subset?]
  (cond
    (not (sequential? actual))
    [:mismatch (model/->Mismatch expected actual)]

    (incorrect-matcher->element-count? subset? (count expected) (count actual))
    ;; for size mismatch, is there a more detailed mismatch model to use?
    [:mismatch (model/->Mismatch expected actual)]

    :else
    (match-all-permutations expected actual subset?)))

(defrecord InAnyOrder [expected]
  Matcher
  (match [_this actual]
    (if-let [validation-issue (validate-input expected actual sequential? 'in-any-order 'sequential)]
      validation-issue
      (match-any-order expected actual false))))

(defrecord EqualsSet [expected]
  Matcher
  (match [_this actual]
    (if-let [validation-issue (validate-input
                                expected actual #(or (set? %) (sequential? %)) set? 'equals 'set)]
      validation-issue
      (match-any-order expected actual false))))

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
    (if-let [validation-issue (validate-input expected actual sequential? 'in-any-order 'sequential)]
      validation-issue
      (selecting-match select-fn expected actual))))

(defrecord PrefixSeq [expected]
  Matcher
  (match [_this actual]
    (if-let [validation-issue (validate-input expected actual sequential? 'prefix-seq  'sequential)]
      validation-issue
      (sequence-match expected actual true))))

(defrecord ContainsSeq [expected]
  Matcher
  (match [_this actual]
    (if-let [validation-issue (validate-input expected actual sequential? 'contains 'sequential)]
      validation-issue
      (match-any-order expected actual true))))

(defrecord ContainsSet [expected]
  Matcher
  (match [_this actual]
    (if-let [validation-issue (validate-input
                                expected actual #(or (set? %) (sequential? %)) set? 'contains 'set)]
      validation-issue
      (match-any-order expected actual true))))
