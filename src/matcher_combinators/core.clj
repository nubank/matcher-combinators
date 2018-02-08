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

(defrecord EqualsMap [expected]
  Matcher
  (select? [_this select-fn candidate]
    (let [selected-matcher (select-fn expected)
          selected-value   (select-fn candidate)]
      (select? selected-matcher select-fn selected-value)))
  (match [_this actual]
    (match-map expected actual model/->Unexpected false)))

(defn- sequence-match [expected actual subseq?]
  (if-not (sequential? actual)
      [:mismatch (model/->Mismatch expected actual)]
      ;; TODO PLM: if we want to pass down matcher types between maps/vectors,
      ;; the `:equals` needs to be dynamically determined
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

(defrecord EqualsSequence [expected]
  Matcher
  (match [_this actual]
    (sequence-match expected actual false)))

(defn- matches-in-any-order? [matchers elements subset? acc]
  (if (empty? matchers)
    [(or subset? (empty? elements)) []]
    (let [[first-element & rest-elements] elements
          matching-matcher (helpers/find-first
                             #(match? (match % first-element))
                             matchers)]
      (if (nil? matching-matcher)
        [false (concat acc matchers)]
        (recur (helpers/remove-first #{matching-matcher} matchers)
               rest-elements
               subset?
               (conj acc matching-matcher))))))

(defn- match-all-permutations [matchers elements subset?]
  (let [perms (helpers/permutations elements)
        result (reduce
                 (fn [acc elements]
                   (let [[res matched-matchers] (matches-in-any-order?
                                                  matchers elements subset? [])]
                     (cond
                       res                         (reduced true)
                       (> (count matched-matchers)
                          (count acc))              matched-matchers

                       :else                        acc)))
                 []
                 perms)]
    (if (boolean? result)
      [true []]
      [false result])))

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
    (let [[res matched-matchers] (match-all-permutations expected actual subset?)]
      (if res
        [:match actual]
        (match (->EqualsSequence matched-matchers) actual)))))

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

(defrecord SubSeq [expected]
  Matcher
  (match [_this actual]
    (sequence-match expected actual true)))

(defrecord SubSet [expected]
  Matcher
  (match [_this actual]
    (match-any-order expected actual true)))
