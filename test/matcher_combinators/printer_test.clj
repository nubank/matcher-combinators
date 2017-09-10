(ns matcher-combinators.printer-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.model :as model]
            [matcher-combinators.core :as core]))

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
      )))
