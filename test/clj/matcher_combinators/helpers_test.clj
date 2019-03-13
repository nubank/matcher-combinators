(ns matcher-combinators.helpers-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.helpers :as helpers]))

(fact "remove-first impl"
  (helpers/remove-first #{1} [3 2 1 1 0]) => [3 2 1 0]
  (helpers/remove-first #{2} [3 2 1 1 0]) => [3 1 1 0]
  (helpers/remove-first #{1 2} [3 2 1 1 0]) => [3 1 1 0]
  (helpers/remove-first (constantly false) [3 2 1 1 0]) => [3 2 1 1 0]
  (helpers/remove-first (constantly true) [3 2 1 1 0]) => [2 1 1 0]
  (helpers/remove-first (constantly true) []) => []
  (helpers/remove-first (constantly false) []) => [])
