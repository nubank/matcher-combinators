(ns matcher-combinators.core
  (:require [matcher-combinators.helpers :as helpers]
            [matcher-combinators.model :as model]))

(defprotocol Matcher
  "For matching expected and actual values, providing helpful mismatch info on
  unsucessful matches"
  (match [this actual]
         "determine if a concrete `actual` value satisfies this matcher"))

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
  (match [_this actual]
   (cond
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
    (if (and (every? (comp match? second) entry-results)
             (or allow-unexpected? (empty? unexpected-entries)))
      [:match actual]
      [:mismatch (->> entry-results
                      (map (fn [[key match-result]] [key (value match-result)]))
                      (concat unexpected-entries)
                      (into actual))])))

(defrecord EmbedsMap [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input expected actual map? 'embeds "map")]
      issue
      (compare-maps expected actual identity true))))

(defrecord EqualsMap [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input expected actual map? 'equals "map")]
      issue
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
    (if-let [issue (validate-input
                     expected actual sequential? 'equals "sequential")]
      issue
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
        matched?                    (reduced ::match-found)
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
    (if (= ::match-found result)
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
    (if-let [issue (validate-input
                     expected actual sequential? 'in-any-order "sequential")]
      issue
      (match-any-order expected actual false))))

(defrecord SetEquals [expected accept-seq?]
  Matcher
  (match [_this actual]
    (if-let [issue (if accept-seq?
                     (validate-input expected
                                     actual
                                     #(or (set? %) (sequential? %))
                                     set?
                                     'set-equals
                                     "set or sequential")
                     (validate-input expected
                                     actual
                                     set?
                                     'equals
                                     "set"))]
      issue
      (let [[matching? result-payload] (match-any-order
                                         (into [] expected) (into [] actual) false)]
        [matching? (into #{} result-payload)]))))

(defrecord Prefix [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input
                     expected actual sequential? 'prefix "sequential")]
      issue
      (sequence-match expected actual true))))

(defrecord EmbedsSeq [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input
                     expected actual sequential? 'embeds "sequential")]
      issue
      (match-any-order expected actual true))))

(defrecord SetEmbeds [expected accept-seq?]
  Matcher
  (match [_this actual]
    (if-let [issue (if accept-seq?
                     (validate-input expected
                                     actual
                                     #(or (set? %) (sequential? %))
                                     set?
                                     'set-embeds
                                     "set or sequential")
                     (validate-input expected
                                     actual
                                     set?
                                     'embeds
                                     "set"))]
      issue
      (let [[matching? result-payload] (match-any-order
                                         (into [] expected) (into [] actual) true)]
        [matching? (into #{} result-payload)]))))
