(ns matcher-combinators.core
  (:require [clojure.math.combinatorics :as combo]
            [clojure.pprint]
            [clojure.spec.alpha :as s]
            [matcher-combinators.model :as model]
            [matcher-combinators.result :as result]
            [matcher-combinators.utils :as utils]))

(defprotocol Matcher
  "For internal use. Type-specific implementations for finding matchers for
  expected values and matching them against expected values."
  (-matcher-for
    [expected]
    [expected t->m]
    "Do not call directly. Implementation for matcher-combinators.matchers/matcher-for.")
  (-base-name [this]
    "The name of the matcher as a symbol")
  (-match [this actual]
    "Do not call directly. Implementation for matcher-combinators.core/match."))

(defn match
  "For internal use. Returns a map indicating whether the `actual` value matches `expected`.

  `expected` can be the expected value, a matcher, or a predicate fn of actual.

  Return map includes the following keys:

  - :matcher-combinators.result/type  - either :match or :mismatch
  - :matcher-combinators.result/value - the actual value with mismatch annotations.
                                        Only present when :match/result is :mismatch"
  [expected actual]
  (-match expected actual))

(s/fdef indicates-match?
  :args (s/cat :match-result ::result/result)
  :ret boolean?)

(defn indicates-match?
  "Returns true if match-result (the map returned by `(match expected actual)`) indicates a match."
  [match-result]
  (= :match (::result/type match-result)))

(defn
  ^{:deprecated true
    :doc "DEPRECATED! Use `indicates-match?` instead."}
  match?
  [match-result]
  (println (str "DEPRECATION NOTICE: matcher-combinators.core/match? is deprecated.\n"
                "                    Use matcher-combinators.core/indicates-match? instead."))
  (indicates-match? match-result))

(defn matcher? [x]
  (satisfies? Matcher x))

(defn- value-match [expected actual]
  (cond
    (= ::missing actual) {::result/type   :mismatch
                          ::result/value  (model/->Missing expected)
                          ::result/weight 1}
    (= expected actual)  {::result/type   :match
                          ::result/value  actual
                          ::result/weight 0}
    :else                {::result/type   :mismatch
                          ::result/value  (model/->Mismatch expected actual)
                          ::result/weight 1}))

(defrecord Value [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [_ actual]
    (value-match expected actual))
  (-base-name [_] 'equals))

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
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input expected actual regex? (constantly true) (-base-name this) regex-type)]
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
           ::result/weight 1}))))
  (-base-name [_] 'regex))

(defrecord Absent []
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [_this _actual]
    ;; `Absent` should never be matched against directly. That happening means
    ;; it wasn't used in the context of a map
    {::result/type  :mismatch
     ::result/value (model/->InvalidMatcherContext
                      "`absent` matcher should only be used as the value in a map")
     ::result/weight 1})
  (-base-name [_] 'absent))

(defmethod clojure.pprint/simple-dispatch Absent [absent]
  (print (-base-name absent)))

(defrecord InvalidType [provided matcher-name type-msg]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [_this _actual]
    {::result/type  :mismatch
     ::result/value (model/->InvalidMatcherType
                     (str "provided: " provided)
                     (str matcher-name
                          " should be called with 'expected' argument of type: "
                          type-msg))
     ::result/weight 1})
  (-base-name [_] (symbol matcher-name)))

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
    (if (and (every? (comp indicates-match? second) entry-results)
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

(def ^:private map-like?
  "Returns true if v is associative, but not sequential. This lets us
  support map-like structures like Datomic EntityMaps without trying
  to compare maps to vectors (which are associative and sequential)."
  (every-pred associative? (complement sequential?)))

(defrecord EmbedsMap [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input expected actual map? map-like? (-base-name this) "map")]
      issue
      (compare-maps expected actual identity true)))
  (-base-name [_] 'embeds))

(defrecord EqualsMap [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input expected actual map? (-base-name this) "map")]
      issue
      (compare-maps expected actual model/->Unexpected false)))
  (-base-name [_] 'equals))

(defrecord EqualsRecord [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input expected actual record? map? (-base-name this) "record")]
      issue
      (if (= (type expected) (type actual))
          (match (->EqualsMap expected) actual)
          {::result/type   :mismatch
           ::result/value  (model/->TypeMismatch expected actual)
           ::result/weight 1})))
  (-base-name [_] 'equals))

(defn- type-preserving-mismatch [base-list values]
  (let [lst (into base-list values)]
    (if (vector? base-list)
      lst
      (reverse lst))))

(def ^:private unexpected-matcher
  (reify Matcher
    (-matcher-for [this] this)
    (-matcher-for [this _] this)
    (-match [_this actual]
      {::result/type   :mismatch
       ::result/value  (model/->Unexpected actual)
       ::result/weight 1})
    (-base-name [_] 'unexpected)))

(defrecord ViaMatcher [transform-actual-fn expected]
    Matcher
    (-matcher-for [_this] (-matcher-for expected))
    (-matcher-for [_this x] (-matcher-for expected x))
    (-match [_ actual]
      (let [transformed (try (transform-actual-fn actual)
                             (catch #?(:clj Exception :cljs js/Error) e e))]
        (if (instance? #?(:clj Exception :cljs js/Error) transformed)
          {::result/type   :mismatch
           ::result/value  (model/->Mismatch (list 'via (-> transform-actual-fn str symbol) expected) actual)
           ::result/weight 1}
          (match expected transformed))))
    (-base-name [_] (-base-name expected)))

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
      (if (some (complement indicates-match?) match-results)
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
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input
                    expected actual sequential? (-base-name this) "sequential")]
      issue
      (sequence-match expected actual false)))
  (-base-name [_] 'equals))

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
       :elements  (concat (map second matching) elements)
       :matched   (map first matching)})
    (let [[matcher & unmatched-rest] unmatched
          matching-elem              (utils/find-first #(indicates-match? (match matcher %))
                                                       elements)]
      (if (nil? matching-elem)
        {:matched?  false
         :unmatched unmatched
         :weight    (residual-matching-weight unmatched elements)
         :elements  (concat (map second matching) elements)
         :matched   (map first matching)}
        (recur unmatched-rest
               (utils/remove-first #(= matching-elem %) elements)
               subset?
               (conj matching [matcher matching-elem]))))))

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
        (better-mismatch? best result) result
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
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input
                    expected actual sequential? (-base-name this) "sequential")]
      issue
      (match-any-order expected actual false)))
  (-base-name [_] 'in-any-order))

(defn- matchable-set?
  "Clojure's set functions expect clojure.lang.IPersistentSet, but
  matching works just fine with java.util.Set as well."
  [s]
  #?(:clj  (or (set? s) (instance? java.util.Set s))
     :cljs (set? s)))

(defrecord SetEquals [expected accept-seq?]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (if accept-seq?
                     (validate-input expected
                                     actual
                                     #(or (matchable-set? %) (sequential? %))
                                     matchable-set?
                                     (-base-name this)
                                     "set or sequential")
                     (validate-input expected
                                     actual
                                     matchable-set?
                                     (-base-name this)
                                     "set"))]
      issue
      (let [{::result/keys [type value weight]} (match-any-order
                                                 (vec expected) (vec actual) false)]
        {::result/type   type
         ::result/value  (set value)
         ::result/weight weight})))
  (-base-name [_] (if accept-seq? 'set-equals 'equals)))

(defrecord Prefix [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input
                    expected actual sequential? (-base-name this) "sequential")]
      issue
      (sequence-match expected actual true)))
  (-base-name [_] 'prefix))

(defrecord EmbedsSeq [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input
                    expected actual sequential? (-base-name this) "sequential")]
      issue
      (match-any-order expected actual true)))
  (-base-name [_] 'embeds))

(defrecord SetEmbeds [expected accept-seq?]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (if accept-seq?
                     (validate-input expected
                                     actual
                                     #(or (matchable-set? %) (sequential? %))
                                     matchable-set?
                                     (-base-name this)
                                     "set or sequential")
                     (validate-input expected
                                     actual
                                     matchable-set?
                                     (-base-name this)
                                     "set"))]
      issue
      (let [{::result/keys [type value weight]} (match-any-order
                                                 (vec expected) (vec actual) true)]
        {::result/type   type
         ::result/value  (set value)
         ::result/weight weight})))
  (-base-name [_] (if accept-seq? 'set-embeds 'embeds)))

(defrecord PredMatcher [pred desc]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (cond
      (= actual ::missing)
      {::result/type  :mismatch
       ::result/value (model/->Missing desc)
       ::result/weight 1}

      (pred actual)
      {::result/type   :match
       ::result/value  actual
       ::result/weight 0}

      :else
      {::result/type  :mismatch
       ::result/value (model/->Mismatch desc actual)
       ::result/weight 1}))
  (-base-name [_] 'predicate))

(defn- printable-matcher [matcher]
  (try
    (if-let [n (-base-name matcher)]
      `(~(symbol n) ~(:expected matcher))
      matcher)
    (catch #?(:clj IllegalArgumentException :cljs js/Error) _e
      matcher)))

(defrecord Mismatcher
  [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (let [{::result/keys [type weight] :as result} (match expected actual)]
      (if (= :match type)
        {::result/type   :mismatch
         ::result/value  (model/->ExpectedMismatch
                          (printable-matcher expected)
                          actual)
         ::result/weight weight}
        {::result/type   :match
         ::result/value  actual
         ::result/weight 0})))
  (-base-name [_] 'mismatch))

(defrecord CljsUriEquals [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input
                    expected actual uri? (-base-name this) "goog.Uri")]
      issue
      (value-match (.toString expected)
                   (.toString actual))))
  (-base-name [_] 'equals))
