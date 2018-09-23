(ns matcher-combinators.core
  (:require [clojure.math.combinatorics :as combo]
            [clojure.spec.alpha :as s]
            [matcher-combinators.result :as result]
            [matcher-combinators.helpers :as helpers]
            [matcher-combinators.model :as model]))

(defprotocol Matcher
  "For matching expected and actual values, providing helpful mismatch info on
  unsucessful matches"
  (match [this actual]
         "determine if a concrete `actual` value satisfies this matcher"))

(s/fdef match?
  :args (s/cat :match-result ::result/result)
  :ret boolean?)
(defn match? [{::result/keys [type]}]
  (= :match type))

(defn- mismatch? [{::result/keys [type]}]
  (= :mismatch type))

(defn matcher? [x]
  (satisfies? Matcher x))

(defn- value [{::result/keys [value]}]
  value)

(defrecord Value [expected]
  Matcher
  (match [_this actual]
   (cond
     (= ::missing actual) {::result/type   :mismatch
                           ::result/value  (model/->Missing expected)
                           ::result/weight 1}
     (= expected actual)  {::result/type   :match
                           ::result/value  actual
                           ::result/weight 0}
     :else                {::result/type   :mismatch
                           ::result/value  (model/->Mismatch expected actual)
                           ::result/weight 1})))

(defn- validate-input
  ([expected actual pred matcher-name type]
   (validate-input expected actual pred pred matcher-name type))
  ([expected actual expected-pred actual-pred matcher-name type]
   (cond
     (= actual ::missing)
     {::result/type  :mismatch
      ::result/value (model/->Missing expected)
      ::result/weight 1}

     (not (expected-pred expected))
     {::result/type  :mismatch
      ::result/value (model/->InvalidMatcherType
                       (str "provided: " expected)
                       (str matcher-name
                            " should be called with 'expected' argument of type: "
                            type))
      ::result/weight 1}

     (not (actual-pred actual))
     {::result/type  :mismatch
      ::result/value (model/->Mismatch expected actual)
      ::result/weight 1}

     :else
     nil)))

(defn- regex? [value] (instance? java.util.regex.Pattern value))
(defrecord Regex [expected]
  Matcher
  (match [_this actual]
   (if-let [issue (validate-input expected actual regex? (constantly true) 'regex "java.util.regex.Pattern")]
     issue
     (try
       (if-let [match (re-find expected actual)]
         {::result/type   :match
          ::result/value  match
          ::result/weight 0}
         {::result/type  :mismatch
          ::result/value (model/->Mismatch expected actual)
          ::result/weight 1})
       (catch ClassCastException ex
         {::result/type  :mismatch
          ::result/value (model/->InvalidMatcherType
                           (str "provided: " actual)
                           (str "regex " (print-str expected) " can't match 'expected' argument of type: "
                                (type actual)))
          ::result/weight 1})))))

(defrecord InvalidType [provided matcher-name type-msg]
  Matcher
  (match [_this _actual]
    {::result/type  :mismatch
     ::result/value (model/->InvalidMatcherType
                      (str "provided: " provided)
                      (str matcher-name
                           " should be called with 'expected' argument of type: "
                           type-msg))
     ::result/weight 1}))

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
      {::result/type   :match
       ::result/value  actual
       ::result/weight 0}
      {::result/type  :mismatch
       ::result/value (->> entry-results
                           (map (fn [[key match-result]] [key (value match-result)]))
                           (concat unexpected-entries)
                           (into actual))
       ::result/weight 1})))

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

(defn- type-preserving-mismatch [base-list values]
  (let [lst (into base-list values)]
    (if (vector? base-list)
      lst
      (reverse lst))))

(defn- sequence-match [expected actual subseq?]
  (if-not (sequential? actual)
      {::result/type :mismatch
       ::result/value (model/->Mismatch expected actual)
       ::result/weight 1}
      (let [matcher-fns     (concat (map #(partial match %) expected)
                                    (repeat (fn [extra-element]
                                              {::result/type :mismatch
                                               ::result/value (model/->Unexpected extra-element)
                                               ::result/weight 1})))
            actual-elements (concat actual (repeat ::missing))
            match-results'  (map (fn [match-fn actual-element] (match-fn actual-element))
                                 matcher-fns actual-elements)
            match-size      (if subseq?
                              (count expected)
                              (max (count actual) (count expected)))
            match-results   (take match-size match-results')]
        (if (some mismatch? match-results)
          {::result/type :mismatch
           ::result/value (type-preserving-mismatch (empty actual) (map value match-results))
           ::result/weight 1}
          {::result/type   :match
           ::result/value  actual
           ::result/weight 0}))))

(defrecord EqualsSeq [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input
                     expected actual sequential? 'equals "sequential")]
      issue
      (sequence-match expected actual false))))

(defn- matched-successfully? [unmatched elements subset?]
  (or (and subset? (empty? unmatched))
      (and (not subset?) (empty? unmatched) (empty? elements))))

(defn- matches-in-any-order? [unmatched elements subset? matching]
  (if (or (empty? unmatched) (empty? elements))
    {:matched?  (matched-successfully? unmatched elements subset?)
     :unmatched unmatched
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
  (let [elem-permutations (combo/permutations elements)
        find-best-match   (matched-or-best-matchers matchers subset?)
        result            (reduce find-best-match
                                  {:matched   []
                                   :unmatched matchers
                                   :elements  elements}
                                  elem-permutations)]
    (if (= ::match-found result)
      {::result/type   :match
       ::result/value  elements
       ::result/weight 0}
      (match (->EqualsSeq (concat (:matched result)
                                  (:unmatched result)))
             (:elements result)))))

(defn- match-any-order [expected actual subset?]
  (if (not (sequential? actual))
    {::result/type   :mismatch
     ::result/value  (model/->Mismatch expected actual)
     ::result/weight 1}
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
      (let [{::result/keys [type value weight]} (match-any-order
                                                  (into [] expected) (into [] actual) false)]
        {::result/type   type
         ::result/value  (into #{} value)
         ::result/weight weight}))))

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
      (let [{::result/keys [type value weight]} (match-any-order
                                                  (into [] expected) (into [] actual) true)]
        {::result/type   type
         ::result/value  (into #{} value)
         ::result/weight weight}))))

(defn match-pred [func actual]
  (cond
    (= actual ::missing)
    {::result/type  :mismatch
     ::result/value (model/->Missing func)
     ::result/weight 1}

    (func actual)
    {::result/type   :match
     ::result/value  actual
     ::result/weight 0}

    :else
    {::result/type  :mismatch
     ::result/value (model/->FailedPredicate (str func) actual)
     ::result/weight 1}))
