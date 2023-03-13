(ns matcher-combinators.matchers-test
  (:refer-clojure :exclude [any?])
  (:require [clojure.math.combinatorics :as combo]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [matcher-combinators.core :as c]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.result :as result]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.test-helpers :as test-helpers :refer [no-match? abs-value-matcher]])
  (:import [matcher_combinators.model Mismatch Missing InvalidMatcherType]))

(defn any? [_x] true)

(def now (java.time.LocalDateTime/now))
(def an-id-string "67b22046-7e9f-46b2-a3b9-e68618242864")
(def another-id (java.util.UUID/fromString "8f488446-374e-4975-9670-35ca0a633da1"))
(def response-time (java.time.LocalDateTime/now))

(def nested-map
  {:id {:type :user-id
        :value an-id-string}
   :input {:id {:type :user-id
                :value an-id-string}
           :timestamp now
           :trigger "blabla"}
   :model "sampa_v3"
   :output {:sampa-score 123.4M
            :user-id another-id
            :w-alpha -0.123}
   :response-time response-time
   :version "1.33.7"})

(def a-nested-map nested-map)
(def b-nested-map (assoc-in nested-map [:model] "curitiba"))

(defn mismatch? [actual]
  (instance? Mismatch actual))
(defn missing? [actual]
  (instance? Missing actual))
(defn invalid-type? [actual]
  (instance? InvalidMatcherType actual))

(defn one-mismatch? [mismatch-list]
  (= 1 (count (filter #(or (mismatch? %) (missing? %)) mismatch-list))))

(deftest in-any-order
  (testing "matcher ordering with maximum matchings for diff"
    (is (match?
         (m/equals
          {::result/type   :mismatch
           ::result/value  (m/in-any-order [a-nested-map {:id map? :model mismatch?}])
           ::result/weight number?})
         (c/match (m/in-any-order [a-nested-map b-nested-map])
                  [a-nested-map a-nested-map]))))

  (testing "always prints the match with the fewest number of matchers that don't match"
    (is (every? one-mismatch?
                (map #(::result/value (c/match (m/in-any-order [1 2 3 4]) %))
                     (combo/permutations [1 2 3 500]))))))

(deftest ordering
  (testing "Show how input ordering affects diff size (when it ideally shouldn't)"
    (testing "Given a particular input ordering, in-any-order shows the smallest diff"
      (is (every? one-mismatch?
                  (->> [{:a 2} {:b 2}]
                       (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
                       ::result/value
                       (map vals)))))

    (testing "in-any-order minimization doesn't find the match ordering that leads
        to the smallest diff, but rather the match ordering that leads to the
        smallest number of immediately passing matchers."
      (is (every? one-mismatch?
                  (->> [{:b 2} {:a 2}]
                       (c/match (m/in-any-order [{:a 1} {:a 1 :b 2}]))
                       ::result/value
                       (map vals)))))))

(deftest regex-matching
  (is (match? {::result/type   :match
               ::result/value  {:one "1"}
               ::result/weight 0}
              (c/match (m/equals {:one (m/regex #"1")})
                       {:one "1"})))

  (is (match? {::result/type   :match
               ::result/value  "pref"
               ::result/weight 0}
              (c/match #"^pref" "prefix")))

  (is (match? {::result/type :match
               ::result/value ["hello, world" "world"]
               ::result/weight 0}
              (c/match #"hello, (.*)" "hello, world")))

  (is (match? {::result/type   :mismatch
               ::result/value  {:one mismatch?}
               ::result/weight number?}
              (c/match (m/equals {:one (m/regex #"1")})
                       {:one "2"})))

  (is (match? {::result/type :mismatch
               ::result/value {:one invalid-type?}
               ::result/weight number?}
              (c/match (m/equals {:one (m/regex "1")})
                       {:one "1"})))

  (is (match? {::result/type :mismatch
               ::result/value {:one invalid-type?}
               ::result/weight number?}
              (c/match (m/equals {:one (m/regex #"1")})
                       {:one 2}))))

(deftest mismatch-with-regex
  (testing "mismatch that includes a matching regex shows the match data"
    (is (match? {::result/type :mismatch
                 ::result/value {:two mismatch?
                                 :one (m/embeds ["hello, world" "world"])}
                 ::result/weight number?}
                (c/match (m/equals {:two 2
                                    :one (m/regex #"hello, (.*)")})
                         {:two 1
                          :one "hello, world"})))))

(deftest java-classes
  (testing "matching"
    (is (match? {::result/type :match
                 ::result/value java.lang.String
                 ::result/weight 0}
                (c/match (m/equals java.lang.String)
                         java.lang.String))))
  (testing "mismatching"
    (is (match? {::result/type :mismatch
                 ::result/value {:actual   java.lang.String
                                 :expected java.lang.Number}
                 ::result/weight 1}
                (c/match (m/equals java.lang.Number)
                         java.lang.String)))))

(deftest java-primitives
  (testing "byte-arrays"
    (let [a (byte-array [(byte 0x43) (byte 0x42)])
          b (byte-array [(byte 0x42) (byte 0x43)])]
      (is (match? {::result/type :match
                   ::result/value a
                   ::result/weight 0}
                  (c/match (m/equals a) a)))
      (is (match? {::result/type :mismatch
                   ::result/value {:actual   b
                                   :expected a}
                   ::result/weight 1}
                  (c/match (m/equals a) b))))))

(defrecord Point [x y])
(defrecord BluePoint [x y])

(deftest equals-with-records
  (testing "matching"
    (let [a (->Point 1 2)]
      (is (match? {::result/type :match
                   ::result/value a
                   ::result/weight 0}
                  (c/match (m/equals a) a)))))

  (testing "mismatching with same type and different values"
    (let [a (->Point 1 2)
          b (->Point 2 2)]
      (is (match? {::result/type :mismatch
                   ::result/value {:x {:actual 2
                                       :expected 1}
                                   :y 2}
                   ::result/weight 1}
                  (c/match (m/equals a) b)))))

  (testing "mismatching with same values and different type"
    (let [a (->Point 1 2)
          b (->BluePoint 1 2)]
      (is (match? {::result/type :mismatch
                   ::result/value {:actual b
                                   :expected a}
                   ::result/weight 1}
                  (c/match (m/equals a) b))))))

(deftest embeds-with-records
  (testing "matching"
    (let [a (->Point 1 2)
          b (->Point 1 2)]
      (is (match? {::result/type :match
                   ::result/value a
                   ::result/weight 0}
                  (c/match (m/embeds b) a)))))

  (testing "matching when a map is expected"
    (let [a (->Point 1 2)
          b {:x 1}]
      (is (match? {::result/type :match
                   ::result/value a
                   ::result/weight 0}
                  (c/match (m/embeds b) a)))))

  (testing "mismatching as records are not allowed to have missing properties"
    (let [a (->Point 1 2)
          b (map->Point {:x 1})]
      (is (match? {::result/type :mismatch
                   ::result/value {:x 1
                                   :y {:actual nil :expected 2}}
                   ::result/weight 1}
                  (c/match (m/equals a) b)))))

  (testing "mismatching with same values and different type"
    (let [a (->Point 1 2)
          b (->BluePoint 1 2)]
      (is (match? {::result/type :mismatch
                   ::result/value {:actual b
                                   :expected a}
                   ::result/weight 1}
                  (c/match (m/equals a) b))))))

(defspec matcher-for-most-cases
  {:doc "matchers/equals is the default matcher for everything but functions, regexen, and maps."
   :num-tests 1000
   :max-size  10}
  (prop/for-all [v (gen/such-that
                    (fn [v] (and (not (map? v))
                                 (not (instance? java.util.regex.Pattern v))
                                 (not (fn? v))))
                    gen/any)]
    (= m/equals
       (m/matcher-for v)
       (m/matcher-for v {}))))

(deftest matcher-for-special-cases
  (testing "matcher for a fn is pred"
    (is (= (class (m/pred (fn [])))
           (class (m/matcher-for (fn []))))))
  (testing "matcher for a map is embeds"
    (is (= m/embeds
           (m/matcher-for {}))))
  (testing "matcher for a regex"
    (is (= m/regex
           (m/matcher-for #"abc")))))

(deftest match-with-matcher
  (testing "processes overrides in order"
    (let [matcher (m/match-with [pos? abs-value-matcher
                                 integer? m/equals]
                                5)]
      (is (match? matcher 5))
      (is (match? matcher -5)))
    (let [matcher (m/match-with [pos? abs-value-matcher
                                 integer? m/equals]
                                -5)]
      (is (no-match? matcher 5))
      (is (match? matcher -5))))
  (testing "maps"
    (testing "passing case with equals override"
      (is (match? (m/match-with [map? m/equals]
                                {:a :b})
                  {:a :b}))
      (testing "legacy API support (map of type to matcher)"
        (is (match? (m/match-with {clojure.lang.IPersistentMap m/equals}
                                  {:a :b})
                    {:a :b}))))
    (testing "failing case with equals override"
      (is (no-match? (m/match-with [map? m/equals]
                                   {:a :b})
                     {:a :b :d :e})))
    (testing "passing case multiple scopes"
      (is (match?
           {:o (m/match-with [map? m/equals]
                             {:a
                              (m/match-with [map? m/embeds]
                                            {:b :c})})}
           {:o {:a {:b :c :d :e}}
            :p :q})))
    (testing "using `absent` matcher"
      (is (match? (m/match-with [map? m/equals]
                                {:a m/absent
                                 :b :c})
                  {:b :c}))
      (is (match? (m/match-with [map? m/embeds]
                                {:a m/absent})
                  {:b :c}))))

  (testing "sets"
    (testing "passing cases"
      (is (match?
           (m/match-with [set? m/embeds]
                         #{1})
           #{1 2}))

      (is (match?
            (m/match-with [set? m/embeds]
                          #{(m/pred odd?)})
            #{1 2}))
      (is (match?
           (m/match-with [set? m/embeds]
                         #{odd?})
           #{1 2}))))

  (testing "multiple scopes"
    (let [expected
          {:a (m/match-with [map? m/equals]
                            {:b
                             (m/match-with [map? m/embeds
                                            vector? m/embeds]
                                           {:c [odd? even?]})})}]
      (is (match? expected {:a {:b {:c [1 2]}}}))
      (is (match? expected {:a {:b {:c [1 2 3]}}}))
      (is (match? expected {:a {:b {:c [1 2]}}
                            :d :e}))
      (is (match? expected {:a {:b {:c [1 2 3]
                                    :d :e}}
                            :f :g}))
      (is (no-match? expected {:a {:b {:c [1 2]}
                                   :d :e}}))))

  (testing "nested explicit matchers override the match-with matcher specified"
    (let [actual {:a {:b {:c 1}
                      :d {:e {:inner-e {:x 1 :y 2}}
                          :f 5
                          :g 17}}}]
      (is (no-match?
           (m/match-with [map? m/equals]
                         {:a {:b {:c 1}
                              :d (m/embeds {:e {:inner-e {:x 1}}})}})
           actual))
      (is (match?
           (m/match-with [map? m/equals]
                         {:a {:b {:c 1}
                              :d (m/embeds {:e {:inner-e (m/embeds {:x 1 :y 2})}})}})
           actual))
      (is (match?
           (m/match-with [map? m/equals]
                         {:a {:b {:c 1}
                              :d (m/embeds {:e {:inner-e {:x 1 :y 2}}})}})
           actual)))))

(def gen-processable-double
  (gen/double* {:infinite? false :NaN? false}))

(def gen-bigdec
  (gen/fmap #(BigDecimal/valueOf %) gen-processable-double))

(defspec within-delta-common-case
  {:doc       "works for ints, doubles, and bigdecs as delta, expected, or actual"
   :max-size  10}
  (prop/for-all [delta    (gen/one-of [gen/small-integer
                                       gen-processable-double
                                       gen-bigdec])
                 expected (gen/one-of [gen/small-integer
                                       gen-processable-double
                                       gen-bigdec])]
    (c/indicates-match?
     (c/match
       (m/within-delta delta expected)
       (+ expected delta)))))

(deftest within-delta-edge-cases
  (testing "+/-infinity and NaN return false (instead of throwing)"
    (is (no-match? (m/within-delta 0.1 100) Double/POSITIVE_INFINITY))
    (is (no-match? (m/within-delta 0.1 100) Double/NEGATIVE_INFINITY))
    (is (no-match? (m/within-delta 0.1 100) Double/NaN))))

(deftest within-delta-in-match-with
  (testing "works with a vec"
    (is (match? (m/match-with [number? (m/within-delta 0.01M)]
                              [{:b 1M} {:b 0M} {:b 3M}])
                [{:b 1M} {:b 0M} {:b 3M}])))
  (testing "works with a seq"
    (is (match? (m/match-with [number? (m/within-delta 0.01M)]
                              '({:b 1M} {:b 0M} {:b 3M}))
                [{:b 1M} {:b 0M} {:b 3M}])))
  (testing "works with a set"
    (is (match? (m/match-with [number? (m/within-delta 0.01M)]
                              #{{:b 1M} {:b 0M} {:b 3M}})
                #{{:b 1M} {:b 0M} {:b 3M}}))))

(deftest mismatcher-matcher
  (testing "assert presence of key via double negation"
    (is (match? (m/mismatch {:a m/absent})
                {:a 1})))
  (testing "assert an entry is definitely not in a sequence"
    (is (match? (m/mismatch (m/embeds [even?]))
                [1 3 5 7])))
  (testing "predicate mismatch"
    (is (match? [1 (m/mismatch odd?) 3]
                [1 2 3])))
  (testing "declarative mismatch"
    (is (match? [1 (m/mismatch {:a 1}) 3]
                [1 {:a 2 :b 1} 3])))
  (testing "in-any-order with mismatch"
    (is (match? (m/in-any-order
                 [odd? pos? (m/mismatch odd?)])
                [1 2 3]))))

(deftest via-matcher
  (testing "without via things are annoying"
    (let [result {:payloads ["{:foo :bar :baz :qux}"]}]
      (is (match? {:payloads [{:foo :bar}]}
                  (update result :payloads (partial map read-string))))))
  (testing "normal usage"
    (is (match? {:payloads [(m/via read-string {:foo :bar})]}
                {:payloads ["{:foo :bar :baz :qux}"]})))

  (testing "via + match-with allows pre-processing `actual` before applying matching"
    (is (match? (m/match-with
                 [vector? (fn [expected] (m/via sort expected))]
                 {:payloads [1 2 3]})
                {:payloads (shuffle [3 2 1])})))

  (testing "mismatch after parsing string as a map"
    (is (match? {::result/type   :mismatch
                 ::result/value  {:payloads [{:foo mismatch?}]}
                 ::result/weight number?}
                (c/match {:payloads [(m/via read-string {:foo :qux})]}
                         {:payloads ["{:foo :bar}"]}))))

  (testing "erroring shows `(mismatch (expected (via some-fn expected-data))
                                      (actual actual-data))`"
    (is (match? {::result/type   :mismatch
                 ::result/value  {:payloads [mismatch?]}
                 ::result/weight number?}
                (c/match {:payloads [(m/via read-string {:foo :barz})]}
                         {:payloads [1]})))))

(deftest seq-of-matcher
  (is (match? {::result/type   :mismatch
               ::result/value  {:expected "seq-of expects a non-empty sequence" :actual []}
               ::result/weight number?}
        (c/match (m/seq-of integer?) [])))
  (is (match? (m/seq-of {:name string? :id (partial instance? java.util.UUID)})
              [{:name "Michael"
                :id    #uuid "c70e35eb-9eb6-4e3d-b5da-1f7f80932db9"}])))

(deftest any-of-matcher
  (testing "simple any-of match"
    (is (match? {::result/type  :match
                 ::result/value {:a 3}}
                (c/match (m/any-of {:a 1} {:a 3}) {:a 3}))))
  (testing "top-level use of `any-of` gives poor mismatch info"
    (is (match? {::result/type  :mismatch
                 ::result/value {:expected (list 'any-of {:a 1} {:a 2})
                                 :actual {:a 3}}}
                (c/match (m/any-of {:a 1} {:a 2}) {:a 3}))))
  (testing "low-level use of `any-of` gives better mismatch info"
    (is (match? {::result/type  :mismatch
                 ::result/value {:a {:expected (list 'any-of 1 2)
                                     :actual 3}}}
                (c/match {:a (m/any-of 1 2)} {:a 3}))))

  (testing "`any-of` + `seq-of` works great"
    (is (match? {::result/type :mismatch
                 ::result/value [1 "2" mismatch? 4 "5" mismatch?]}
          (c/match (m/seq-of (m/any-of string? integer?))
                   [1 "2" :3 4 "5" :6])))
    (is (match? (m/seq-of (m/any-of string? integer?))
                [1 "2" 3 "4"]))))

(deftest all-of-matcher
  (testing "simple all-of match"
    (is (match? {::result/type  :match
                 ::result/value {:a 3}}
                (c/match (m/all-of {:a odd?} {:a 3}) {:a 3}))))
  (testing "top-level use of `all-of` gives poor mismatch info"
    (is (match? {::result/type  :mismatch
                 ::result/value {:expected (list 'all-of {:a 3} {:a 2})
                                 :actual {:a 3}}}
                (c/match (m/all-of {:a 3} {:a 2}) {:a 3}))))
  (testing "low-level use of `all-of` gives better mismatch info"
    (is (match? {::result/type  :mismatch
                 ::result/value {:a {:expected (list 'all-of (m/equals integer?) (m/equals odd?) any?)
                                     :actual 3}}}
                (c/match {:a (m/all-of integer? odd? #(> % 5))} {:a 3}))))

  (testing "`all-of` + `seq-of` works great"
    (is (match? {::result/type :mismatch
                 ::result/value [1 mismatch? mismatch? mismatch? mismatch? mismatch?]}
          (c/match (m/seq-of (m/all-of integer? odd?))
                   [1 "2" :3 4 "5" :6])))
    (is (match? (m/seq-of (m/all-of integer? odd?))
                [1 -1 3 -3]))))

(deftest pred-matcher
  (testing "pred matcher without description argument gives mismatch info with the pred function's object representation"
    (is (match? {::result/type   :mismatch
                 ::result/value  {:payloads {:expected (list 'pred ifn?) :actual -1}}
                 ::result/weight number?}
                (c/match {:payloads (m/pred pos?)}
                         {:payloads -1}))))
  (testing "you can provide a description for clearer mismatch info"
    (is (match? {::result/type   :mismatch
                 ::result/value  {:payloads {:expected "positive numbers only please" :actual -1}}
                 ::result/weight number?}
                (c/match {:payloads (m/pred pos? "positive numbers only please")}
                         {:payloads -1})))))
