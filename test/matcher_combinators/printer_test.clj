(ns matcher-combinators.printer-test
  (:require [midje.sweet :refer :all]
            [midje.experimental :refer [for-all]]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.model :as model]
            [matcher-combinators.core :as core]
            [clojure.test.check.generators :as gen]
            [colorize.core :as colorize]
            [clojure.pprint :as pprint]))

(def simple-double (gen/double* {:infinite? false
                                 :NaN?      false}))

(def scalars
  (gen/one-of [gen/int gen/large-integer simple-double gen/char-ascii
               gen/string-ascii gen/ratio gen/boolean gen/keyword
               gen/keyword-ns gen/symbol gen/symbol-ns gen/uuid]))
(def any (gen/recursive-gen gen/container-type scalars))

(facts "on how we markup expressions that need special coloring"
  (fact "regular clojure expressions are not marked-up at all"
    (for-all [expression any]
      {:num-tests 100}
      (printer/markup-expression expression) => expression))

  (fact "Mismatches are marked up in yellow and red"
    (printer/markup-expression (model/->Mismatch 1 2))
    => (list 'mismatch
             (printer/->ColorTag :yellow 1)
             (printer/->ColorTag :red 2))

    (for-all [expected any
              actual   any]
      {:num-tests 100}
      (printer/markup-expression (model/->Mismatch expected actual))
      => (list 'mismatch
               (printer/->ColorTag :yellow expected)
               (printer/->ColorTag :red actual))))

  (fact "Missing values are marked up in red"
    (printer/markup-expression (model/->Missing 42))
    => (list 'missing
             (printer/->ColorTag :red 42))

    (for-all [expected any]
      {:num-tests 100}
      (printer/markup-expression (model/->Missing expected))
      => (list 'missing
               (printer/->ColorTag :red expected))))

  (fact "Unexpected values are also marked up in red"
    (printer/markup-expression (model/->Unexpected 42))
    => (list 'unexpected
             (printer/->ColorTag :red 42))

    (for-all [actual any]
      {:num-tests 100}
      (printer/markup-expression (model/->Unexpected actual))
      => (list 'unexpected
               (printer/->ColorTag :red actual))))

  (fact "Failed predicate"
    (printer/markup-expression (model/->FailedPredicate '(roughly 1) 2))
    => (list 'predicate
             (printer/->ColorTag :yellow '(roughly 1))
             (printer/->ColorTag :red 2))))

(midje.config/with-augmented-config {:partial-prerequisites true}
  (facts "On printing"
    (fact "When an expression can be marked up, will use color tags to as-string in color"
      (printer/as-string ..markupable..)
      => (str "(foo " (colorize/red "bar") ")" "\n")
      (provided
        (printer/markup-expression ..markupable..)
        => (list 'foo (printer/->ColorTag :red 'bar))))

    (fact "When printing a regular expression, just pprint it"
      (printer/as-string {:aaaaaaaaaaaa [100000000 100000000 100000000 100000000 100000000]
                      :bbbbbbbbbbbb [200000000 200000000 200000000 200000000 200000000]})
      => "{:aaaaaaaaaaaa [100000000 100000000 100000000 100000000 100000000],\n :bbbbbbbbbbbb [200000000 200000000 200000000 200000000 200000000]}\n"

      (for-all [exp any]
        {:num-tests 100}
        (printer/as-string exp) => (with-out-str (pprint/pprint exp))))))
