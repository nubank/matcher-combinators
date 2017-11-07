(ns midje-generative
  (:require [midje.sweet :refer :all]

            [midje.emission.state :as m-state]
            [midje.emission.api :as emission]

            [midje.emission.plugins.util :as util]

            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]

            [clojure.string :as string])
  (:import [java.io StringWriter]))

(defmacro silently [& forms]
  `(let [output-counters-before# (m-state/output-counters)
         writer# (new StringWriter)]
     (binding [clojure.test/*test-out* writer#]
       (let [result# (do ~@forms)]
         (m-state/set-output-counters! output-counters-before#)
         result#))))

; from midje.emission.plugins.util
(defn- format-binding-map [binding-map]
  (let [formatted-entries (for [[k v] binding-map]
                            (str (pr-str k) " " (pr-str v)))]
    (str "[" (string/join "\n                        " formatted-entries) "]")))

(defn log-error [names values]
  (when (emission/config-above? :print-nothing)
    (util/emit-one-line (str "With generated values: "
                             (format-binding-map (zipmap names values))))))

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

(comment
  (midje.emission.state/reset-output-counters!)
  (midje.emission.state/output-counters)
  )
