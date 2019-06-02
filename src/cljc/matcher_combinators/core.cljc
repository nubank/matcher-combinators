(ns matcher-combinators.core
  (:require [clojure.math.combinatorics :as combo]
            [clojure.spec.alpha :as s]
            [matcher-combinators.result :as result]
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

(defn- regex? [value]
  #?(:clj  (instance? java.util.regex.Pattern value)
     :cljs (regexp? value)))

(def regex-type
  #?(:clj  "java.util.regex.Pattern"
     :cljs "RegExp"))

(defrecord Regex [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input expected actual regex? (constantly true) 'regex regex-type)]
      issue
      (try
        (if-let [match (re-find expected actual)]
          {::result/type   :match
           ::result/value  match
           ::result/weight 0}
          {::result/type  :mismatch
           ::result/value (model/->Mismatch expected actual)
           ::result/weight 1})
        (catch #?(:clj ClassCastException, :cljs js/Error) _
          {::result/type  :mismatch
           ::result/value (model/->InvalidMatcherType
                           (str "provided: " actual)
                           (str "regex " (print-str expected) " can't match 'expected' argument of type: "
                                (type actual)))
           ::result/weight 1})))))

(defrecord Absent []
  Matcher
  (match [_this _actual]
    ;; `Absent` should never be matched against directly. That happening means
    ;; it wasn't used in the context of a map
    {::result/type  :mismatch
     ::result/value (model/->InvalidMatcherContext
                      "`absent` matcher should only be used as the value in a map")
     ::result/weight 1}))

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

(defn- find-unexpected [expected-map key]
  (when-let [[k v] (find expected-map key)]
    (when-not (= Absent (type v)) [k v])))

(defn- match-kv [actual [key matcher]]
  (if (= Absent (type matcher))
    (if-let [[k v] (find actual key)]
      [key {::result/type   :mismatch
            ::result/value  (model/->Unexpected v)
            ::result/weight 1}]
      nil)
    [key (match matcher (get actual key ::missing))]))

(defn- compare-maps [expected actual unexpected-handler allow-unexpected?]
  (let [entry-results      (->> expected
                                (map (partial match-kv actual))
                                (filter identity))
        unexpected-entries (keep (fn [[key val]]
                                   (when-not (find-unexpected expected key)
                                     [key (unexpected-handler val)]))
                                 actual)]
    (if (and (every? (comp match? second) entry-results)
             (or allow-unexpected? (empty? unexpected-entries)))
      {::result/type   :match
       ::result/value  actual
       ::result/weight 0}
      (let [mismatch-val (->> entry-results
                              (map (fn [[key match-result]] [key (::result/value match-result)]))
                              (concat unexpected-entries)
                              (into actual))
            weight        (->> entry-results
                               (map second)
                               (reduce (fn [acc-weight {::result/keys [weight]}] (+ acc-weight weight))
                                       (if allow-unexpected? 0 (count unexpected-entries))))]
        {::result/type   :mismatch
         ::result/value  mismatch-val
         ::result/weight weight}))))

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

(defn- match-record [expected actual matcher]
  (if (= (type expected) (type actual))
    (match (matcher expected) actual)
    {::result/type   :mismatch
     ::result/value  (model/->TypeMismatch expected actual)
     ::result/weight 1}))

(defrecord EqualsRecord [expected]
  Matcher
  (match [_this actual]
    (if-let [issue (validate-input expected actual record? map? 'equals "record")]
      issue
      (cond
        (record? actual)
        (match-record expected actual ->EqualsMap)

        :else
        (match (->EqualsMap expected) actual)))))

(defn- type-preserving-mismatch [base-list values]
  (let [lst (into base-list values)]
    (if (vector? base-list)
      lst
      (reverse lst))))

(def ^:private unexpected-matcher
  (reify Matcher
    (match [_this actual]
      {::result/type   :mismatch
       ::result/value  (model/->Unexpected actual)
       ::result/weight 1})))

(defn- normalize-inputs-length
  "Modify the matchers and actuals sequences to match in length.
  When `matchers` is longer, add `missing` elements to `actuals`.
  When `actuals` is longer, add unexpected entry matchers to `matchers`."
  [matchers actuals]
  (let [matchers-count (count matchers)
        actuals-count  (count actuals)]
    (if (< actuals-count matchers-count)
      [matchers
       (take matchers-count (concat actuals (repeat ::missing)))]
      [(take actuals-count (concat matchers (repeat unexpected-matcher)))
       actuals])))

(defn- sequence-match [expected actual subseq?]
  (if-not (sequential? actual)
    {::result/type   :mismatch
     ::result/value  (model/->Mismatch expected actual)
     ::result/weight 1}
    (let [[matchers
           actual-elems] (normalize-inputs-length expected actual)
          match-results' (map (fn [matcher actual-element] (match matcher actual-element))
                              matchers actual-elems)
          match-size     (if subseq?
                           (count expected)
                           (max (count actual) (count expected)))
          match-results  (take match-size match-results')]
      (if (some mismatch? match-results)
        {::result/type   :mismatch
         ::result/value  (type-preserving-mismatch (empty actual) (map ::result/value match-results))
         ::result/weight (->> match-results
                              (map ::result/weight)
                              (reduce + 0))}
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

(defn- residual-matching-weight [matchers elements]
  (reduce (fn [w {::result/keys [weight]}] (+ w weight))
          0
          (map match matchers elements)))

(defn- matches-in-any-order? [unmatched elements subset? matching]
  (if (or (empty? unmatched) (empty? elements))
    (let [matched? (matched-successfully? unmatched elements subset?)]
      {:matched?  matched?
       :unmatched unmatched
       :weight    (if matched? 0 (residual-matching-weight unmatched elements))
       :matched   matching})
    (cond
      (match? (match (first unmatched) (first elements)))
      (recur (rest unmatched)
             (rest elements)
             subset?
             (conj matching (first unmatched)))

      subset?
      (recur unmatched
             (rest elements)
             subset?
             matching)

      :else
      {:matched?  false
       :unmatched unmatched
       :weight    (residual-matching-weight unmatched elements)
       :matched   matching})))

(defn- better-mismatch? [best candidate]
  (let [best-matched      (-> best :matched count)
        candidate-matched (-> candidate :matched count)
        candidate-weight  (:weight candidate)
        best-weight       (:weight best)]
    (and (>= candidate-matched best-matched)
         (<= candidate-weight best-weight))))

(defn- matched-or-best-matchers [elements subset?]
  (fn [best matchers]
    (let [{:keys [matched?] :as result} (matches-in-any-order? matchers elements subset? [])]
      (cond
        matched?                       (reduced ::match-found)
        (better-mismatch? best result) (assoc result :elements elements)
        :else                          best))))

(defn- match-all-permutations [expected elements subset?]
  (let [[matchers elements] (if subset?
                              [expected elements]
                              (normalize-inputs-length expected elements))
        matcher-perms       (combo/permutations matchers)
        find-best-match     (matched-or-best-matchers elements subset?)
        result              (reduce find-best-match
                                    {:matched   []
                                     :weight    #?(:clj Integer/MAX_VALUE
                                                   :cljs (.-MAX_SAFE_INTEGER js/Number))
                                     :elements  elements
                                     :unmatched matchers}
                                    matcher-perms)]
    (if (= ::match-found result)
      {::result/type   :match
       ::result/value  elements
       ::result/weight 0}
      (match (->EqualsSeq (concat (:matched result)
                                  (:unmatched result)))
        (:elements result)))))

(defn- match-any-order [expected actual subset?]
  (if-not (sequential? actual)
    {:result/type   :mismatch
     :result/value  (model/->Mismatch expected actual)
     :result/weight 1}
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
                                                  (vec expected) (vec actual) false)]
        {::result/type   type
         ::result/value  (set value)
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
                                                 (vec expected) (vec actual) true)]
        {::result/type   type
         ::result/value  (set value)
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
