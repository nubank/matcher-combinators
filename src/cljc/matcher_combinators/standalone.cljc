(ns matcher-combinators.standalone
  "An API for using matcher-combinators outside the context of a test framework"
  (:require [matcher-combinators.core :as core]
            #?(:cljs [matcher-combinators.cljs-test :as internal.clj-test]
               :clj  [matcher-combinators.clj-test :as internal.clj-test])
            #?(:cljs [cljs.test :as t]
               :clj  [clojure.test :as t])
            [matcher-combinators.parser]))

(defn match
  "Returns a map indicating whether the `actual` value matches `expected`.

  `expected` can be the expected value, a matcher, or a predicate fn of actual.

  Return map includes the following keys:

  - :match/result          - either :match or :mismatch
  - :mismatch/detail       - the actual value with mismatch annotations.
  - ::pretty-print!        - function that pretty prints the mismatch
  - ::report-clojure-test! - function that reports failure to clojure.test"
  [matcher actual]
  (let [{:keys [matcher-combinators.result/type
                matcher-combinators.result/value]
         :as match-data}
        (core/match matcher actual)
        printable-match-data (internal.clj-test/tagged-for-pretty-printing
                              nil
                              match-data)]
    (cond-> {:match/result type
             ::report-clojure-test! #(t/do-report
                                       (if (= :mismatch type)
                                         {:type     :fail
                                          :expected matcher
                                          :actual   printable-match-data}
                                         {:type     :pass
                                          :expected matcher
                                          :actual   (list 'match? matcher actual)}))}
      (= :mismatch type)
      (assoc :mismatch/detail value
             ::pretty-print! #(print printable-match-data)))))

(defn report-clojure-test!
  "Given a `match`, report the result to clojure.test"
  [{report! ::report-clojure-test! :as _match-result}]
  (report!))

(defn print!
  "Given a `match`, pretty-print the mismatch data-structure"
  [{pretty-print! ::pretty-print! :as _match-result}]
  (pretty-print!))

(comment
  (def result (match {:name "Quercus suber"} {:name "Quercus lusitanica"}))
  (print! result)
  (report-clojure-test! result))

(defn match?
  "Given a `matcher` and `actual`, returns `true` if
  `(match matcher actual)` results in a match. Else, returns `false.`

  Given only a `matcher`, returns a function that will
  return true or false by the same logic."
  ([matcher]
   (fn [actual] (match? matcher actual)))
  ([matcher actual]
   (core/indicates-match? (core/match matcher actual))))
