(ns matcher-combinators.checkers-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.core :as c]
            [matcher-combinators.checkers :as ch]))

(fact "sequence matching"
  [] => (ch/match [])
  [1] => (ch/match [1])
  [[1]] => (ch/match [[1]])
  [[[1]]] => (ch/match [[[1]]])
  [[[1]]] => (ch/match [[[(c/checker->matcher odd?)]]]))

(fact "map matching"
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb 1} :c 2})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb (c/checker->matcher odd?)} :c 2}))

(fact "map embeds"
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb 1}})
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (c/checker->matcher odd?)}}))

(fact "map in a sequence in a map"
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2 :dd 3}] :b 4})
  ;; Should the following 2 tests pass?
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2}]})
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2}] :b 4})

  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/match {:a [{:bb 1} {:cc 2 :dd 3}] :b 4}))
