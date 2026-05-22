(ns matcher-combinators.core
  (:require [clojure.pprint]
            [clojure.string :as string]
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

(defn- with-mismatch-meta
  "Tags element with data that allows abbreviation of matched data-structures
  in test output when desired"
  [elem mismatch-meta]
  (if #?(:clj (instance? clojure.lang.IMeta elem)
         :cljs (satisfies? IMeta elem))
    (with-meta elem {:mismatch mismatch-meta})
    elem))

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
      (let [mismatches (->> entry-results
                              (map (fn [[key match-result]] [key (::result/value match-result)]))
                              (concat unexpected-entries))
            mismatch-val (try (into actual mismatches)
                              (catch #?(:clj AbstractMethodError :cljs js/Error) _ame
                                ;; converts things like Datomic EntityMaps into
                                ;; maps, so assoc'ing can happen
                                (into (into {} actual) mismatches)))
            weight        (->> entry-results
                               (map second)
                               (reduce (fn [acc-weight result] (+ acc-weight (::result/weight result)))
                                       (if allow-unexpected? 0 (count unexpected-entries))))]
        {::result/type   :mismatch
         ::result/value  (with-mismatch-meta mismatch-val :mismatch-map)
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
    (if (or (vector? base-list)
            (set? base-list))
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

(def ^:private pass-through-matcher
  (reify Matcher
    (-matcher-for [this] this)
    (-matcher-for [this _] this)
    (-match [_this actual]
      {::result/type   :match
       ::result/value  (model/->Extra actual)
       ::result/weight 0})
    (-base-name [_] 'extra)))

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
       ::result/value  (with-mismatch-meta
                         (type-preserving-mismatch (empty actual) (map ::result/value match-results))
                         :mismatch-sequence)
       ::result/weight (->> match-results
                            (map ::result/weight)
                            (reduce + 0))}
      {::result/type   :match
       ::result/value  actual
       ::result/weight 0})))

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

(defrecord SeqOf [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input expected actual (constantly true) sequential? (-base-name this) "sequential")]
      issue
      (if (seq actual)
        (sequence-match (repeat (count actual) expected) actual false)
        {::result/type  :mismatch
         ::result/value (model/->Mismatch "seq-of expects a non-empty sequence" actual)
         ::result/weight 1})))
  (-base-name [_] 'seq-of))

(defn- build-match-matrix [matchers elements]
  (mapv (fn [m] (mapv #(match m %) elements)) matchers))

(defn- try-augment [matcher-idx matrix match-to visited]
  (let [num-elems (count (get matrix 0 []))]
    (loop [elem-idx 0
           match-to match-to
           visited  visited]
      (cond
        (>= elem-idx num-elems)
        [false match-to visited]

        (or (contains? visited elem-idx)
            (not (indicates-match? (get-in matrix [matcher-idx elem-idx]))))
        (recur (inc elem-idx) match-to visited)

        :else
        (let [visited+elem                      (conj visited elem-idx)
              current-owner                     (get match-to elem-idx -1)
              [path-found? match-to' visited']  (if (neg? current-owner)
                                                  [true match-to visited+elem]
                                                  (try-augment current-owner matrix match-to visited+elem))]
          (if path-found?
            [true (assoc match-to' elem-idx matcher-idx) visited']
            (recur (inc elem-idx) match-to visited')))))))

(defn- max-bipartite-matching [matrix]
  (reduce (fn [match-to matcher-idx]
            (let [[_ match-to'] (try-augment matcher-idx matrix match-to #{})]
              match-to'))
          {}
          (range (count matrix))))

(defn- perms-of [v]
  (if (empty? v)
    [[]]
    (for [i (range (count v))
          p (perms-of (into (subvec v 0 i) (subvec v (inc i))))]
      (into [(nth v i)] p))))

(def ^:private max-perm-k
  ;; Measured on JVM (Apple M-series): k=8 → ~120ms avg on failure path.
  ;; k=9 → ~2s, k=10 → ~27s. Above this threshold we fall back to greedy:
  ;; diff is real but not guaranteed minimum-cost. Acceptable cost for a failing test.
  8)

(defn- min-cost-assign [unmatched-mi available-ej matrix matchers]
  ;; unexpected-matchers always return weight=1 regardless of element, so their
  ;; assignment order doesn't affect optimality — pair them with leftover elements.
  ;; Only regular matchers need optimal (min-cost) assignment via perms-of.
  (let [groups      (group-by #(identical? (nth matchers %) unexpected-matcher) unmatched-mi)
        regular-mi  (vec (get groups false []))
        extra-mi    (vec (get groups true []))
        ejs         (vec available-ej)
        k           (min (count regular-mi) (count ejs))
        regular-ejs (subvec ejs 0 k)
        extra-ejs   (subvec ejs k)]
    (if (zero? k)
      (mapv vector extra-mi extra-ejs)
      (let [cost (fn [pairs]
                   (reduce (fn [acc [mi ej]]
                             (+ acc (::result/weight (get-in matrix [mi ej]))))
                           0 pairs))]
        (into (if (> k max-perm-k)
                (mapv vector regular-mi regular-ejs)
                (->> (perms-of regular-ejs)
                     (map (fn [perm] (mapv vector regular-mi perm)))
                     (reduce (fn [best a] (if (< (cost a) (cost best)) a best)))))
              (mapv vector extra-mi extra-ejs))))))

(defn- matchers+elems-for-subset [expected elements]
  (let [n (count expected)
        m (count elements)]
    [(vec expected)
     (vec (if (>= m n)
            elements
            (take n (concat elements (repeat ::missing)))))]))

(defn- match-all-permutations [expected elements subset?]
  (let [[matchers elems] (if subset?
                           (matchers+elems-for-subset expected elements)
                           (mapv vec (normalize-inputs-length expected elements)))
        n        (count matchers)
        matrix   (build-match-matrix matchers elems)
        match-to (max-bipartite-matching matrix)]
    (if (= (count match-to) n)
      {::result/type   :match
       ::result/value  elements
       ::result/weight 0}
      (let [mi->ej       (into {} (map (fn [[ej mi]] [mi ej]) match-to))
            matched-ejs  (set (keys match-to))
            matched-mis  (set (vals match-to))
            unmatched-mi (remove matched-mis (range n))
            unmatched-ej (remove matched-ejs (range (count elems)))
            all-mi->ej   (into mi->ej (min-cost-assign unmatched-mi unmatched-ej matrix matchers))
            truly-extra  (when subset?
                           (remove (set (vals all-mi->ej)) (range (count elems))))
            ordered-mi   (sort (keys all-mi->ej))
            res-matchers (into (mapv #(get matchers %) ordered-mi)
                               (repeat (count truly-extra) pass-through-matcher))
            res-elements (into (mapv #(get elems (get all-mi->ej %)) ordered-mi)
                               (mapv #(get elems %) truly-extra))]
        (update (match (->EqualsSeq res-matchers) res-elements)
                ::result/value
                #(with-mismatch-meta % :mismatch-sequence))))))

(defn- match-any-order [expected actual subset?]
  (if-not (sequential? actual)
    {::result/type   :mismatch
     ::result/value  (model/->Mismatch expected actual)
     ::result/weight 1}
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
      (update (match-any-order (vec expected) (vec actual) false)
              ::result/value
              #(with-meta (set %) (meta %)))))
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
      (update (match-any-order (vec expected) (vec actual) true)
              ::result/value set)))
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

(defrecord AnyOf [matchers]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (reduce (fn [min-mismatch-weight matcher]
              (if (= ::end matcher)
                {::result/type   :mismatch
                 ::result/value  (model/->Mismatch (concat ['any-of] matchers) actual)
                 ::result/weight min-mismatch-weight}
                (let [result (match matcher actual)]
                  (if (indicates-match? result)
                    (reduced {::result/type   :match
                              ::result/value  actual
                              ::result/weight 0})
                    (min (::result/weight result)
                         min-mismatch-weight)))))
            #?(:clj Integer/MAX_VALUE
               :cljs (.-MAX_SAFE_INTEGER js/Number))
            (conj (into [] matchers) ::end)))
  (-base-name [_] 'any-of))

(defrecord AllOf [matchers]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (reduce (fn [_acc matcher]
              (if (= ::end matcher)
                {::result/type   :match
                 ::result/value  actual
                 ::result/weight 0}
                (let [result (match matcher actual)]
                  (when-not (indicates-match? result)
                    (reduced
                      {::result/type   :mismatch
                       ::result/value  (model/->Mismatch (concat ['all-of] matchers) actual)
                       ;; using just one mismatch weight is potentially not so useful:
                       ::result/weight (::result/weight result)})))))
            nil
            (conj (into [] matchers) ::end)))
  (-base-name [_] 'all-of))

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
    (let [result (match expected actual)]
      (if (= :match (::result/type result))
        {::result/type   :mismatch
         ::result/value  (model/->ExpectedMismatch
                          (printable-matcher expected)
                          actual)
         ::result/weight (::result/weight result)}
        {::result/type   :match
         ::result/value  actual
         ::result/weight 0})))
  (-base-name [_] 'mismatch))

(defn- backport-uri?
  "backport uri? to clojure 1.8"
  [x]
  #?(:clj  (instance? java.net.URI x)
     :cljs (instance? goog.Uri x)))

(defrecord CljsUriEquals [expected]
  Matcher
  (-matcher-for [this] this)
  (-matcher-for [this _] this)
  (-match [this actual]
    (if-let [issue (validate-input
                    expected actual backport-uri? (-base-name this) "goog.Uri")]
      issue
      (value-match (.toString expected)
                   (.toString actual))))
  (-base-name [_] 'equals))

(defn non-internal-record? [v]
  (and (record? v)
       (not (string/starts-with? (-> v type str) "class matcher_combinators.core"))))
