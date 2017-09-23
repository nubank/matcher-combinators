(ns matcher-combinators.printer-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.model :as model]
            [matcher-combinators.core :as core]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test.check :as tc]
            [midje.emission.state :as m-state]
            [midje.emission.api :as emission]
            [midje.parsing.1-to-explicit-form.facts :as parse-facts]
            [midje.util.pile :as pile]
            [midje.emission.plugins.util :as util]
            [clojure.string :as str]
            [clojure.string :as string])
  (:import [java.io StringWriter]))

(def gen-atom (gen/one-of [gen/int
                           gen/string
                           gen/symbol
                           gen/symbol-ns
                           gen/keyword
                           gen/boolean
                           gen/ratio
                           gen/char]))

(def gen-seq (gen/vector gen-atom))

(def gen-map (gen/map gen-atom gen-atom))

(def gen-coll (gen/one-of [gen-seq gen-map]))

(def gen-clojure-expression (gen/one-of [gen-coll gen-atom]))

(def prop1
  (prop/for-all [elem gen-clojure-expression]
    (= (printer/markup-expression elem) elem)))

(defmacro silently [& forms]
  `(let [output-counters-before# (m-state/output-counters)
         writer# (new StringWriter)]
     (binding [clojure.test/*test-out* writer#]
       (let [result# (do ~@forms)]
         (m-state/set-output-counters! output-counters-before#)
         result#))))

(resetting-midje-counters (fact 1 => 2))

(defmacro expand-fact-with-substitutions [substitutions form]
  (let [quoted-subs (into {} (map (fn [[k v]] [`(quote ~(symbol (name k))) v]) substitutions))]
    (midje.parsing.1-to-explicit-form.metadata/with-wrapped-metadata {:midje/table-bindings quoted-subs}
                                                                     (macroexpand (parse-facts/wrap-fact-around-body {} form)))))


(expand-fact-with-substitutions {x 1 y 2} (fact 1 => 22))


(facts "scalar values act as equals-value matchers"
  (facts "on how we markup expressions that need special coloring"
    #_(fact "regular clojure expressions are not marked-up at all"
        (tc/quick-check 100 prop1) => (contains {:result true}))

    ))

; copied from midje.emission.plugins.util
(defn- format-binding-map [binding-map]
  (let [formatted-entries (for [[k v] binding-map]
                            (str (pr-str k) " " (pr-str v)))]
    (str "[" (string/join "\n                        " formatted-entries) "]")))

(defn- log-error [names values]
  #_(when (emission/config-above? :print-nothing) (#'emission/bounce-to-plugin :fail-generative {}))
  (util/emit-one-line (str "With generated values: "
                           (format-binding-map (zipmap names values)))))

(fact "regular clojure expressions are not marked-up at all"
  (let [fact-fn (fn [elem] (fact elem => integer?))
        prop    (prop/for-all* [gen/int]
                  fact-fn)
        run     (silently (tc/quick-check 10 prop))]
    (if (:result run)
      (emission/pass)
      (do (log-error '(elem) (-> run nu/tap :shrunk :smallest))
        (apply fact-fn (-> run :shrunk :smallest))))))


(midje.emission.state/reset-output-counters!)
(midje.emission.state/output-counters)


(def ^:dynamic *midje-generative-runs* 10)

(defmacro for-all [& forms]
  (let [bindings    (->> forms first (partition 2))
        checks      (rest forms)
        prop-names  (mapv first bindings)
        prop-values (mapv second bindings)]
    `(let [fact-fn# (fn ~prop-names (fact ~@checks))
           prop#    (prop/for-all* ~prop-values fact-fn#)
           run#     (silently (tc/quick-check *midje-generative-runs* prop#))]
       (if (:result run#)
         (emission/pass)
         (do (log-error '~prop-names (-> run# :shrunk :smallest))
             (apply fact-fn# (-> run# :shrunk :smallest)))))))

(for-all [strictly-pos gen/s-pos-int
          any-integer  gen/int]
  (fact "Summing an integer to a positive integer should be positive? Really?"
    strictly-pos => integer?
    {:x (+ strictly-pos any-integer)} => (contains {:x pos?})))


(fact "regular clojure expressions are not marked-up at all"
  )

(tabular
  (fact ?x => 1)
  ?x
  1
  2)

(macroexpand-1 `(fact "huh" 4220 => 1337))

(midje.checking.checkables/check-one
  (fact "huh" 4220 => 1337) nil)

(comment
  (property "regular clojure expressions are not marked-up at all"
            (prop/for-all [elem gen-clojure-expression]
              (printer/markup-expression elem) => elem)))

(comment
  (println
    (printer/print {:a 10}))

  (println
    (printer/print {:a (model/->Mismatch 10 20)}))




  (println
    (printer/print
      #matcher_combinators.model.Mismatch{:expected [#matcher_combinators.core.Value{:expected 1}
                                                     #matcher_combinators.core.Value{:expected 2}
                                                     #matcher_combinators.core.Value{:expected 3}],
                                          :actual   [1 2]}))

  (println
    (printer/print
      (second
        (core/match
          (core/in-any-order [1 2 3])
          [1 2])
        ))))
