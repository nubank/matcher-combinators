(ns matcher-combinators.checkers-test
  (:require [midje.sweet :refer :all]
            [matcher-combinators.core :as c]
            [matcher-combinators.checkers :as ch]))

(fact "sequence matching"
  [] => (ch/match [])
  [1] => (ch/match [1])
  [[1]] => (ch/match [[1]])
  [[[1]]] => (ch/match [[[1]]])
  [[[1]]] => (ch/match [[[(c/pred->matcher odd?)]]]))

(fact "map matching"
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb 1} :c 2})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb (c/pred->matcher odd?)} :c 2}))

(fact "map embeds"
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb 1}})
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (c/pred->matcher odd?)}}))

(fact "map in a sequence in a map"
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2 :dd 3}] :b 4})
  ;; Should the following 2 tests pass?
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2}]})
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2}] :b 4})

  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/match {:a [{:bb 1} {:cc 2 :dd 3}] :b 4}))

(fact "use midje checkers inside matchers"
  {:a {:bb 1} :c 2} => (ch/match anything)
  {:a {:bb 1} :c 2} => (ch/embeds anything)
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb anything} :c 2})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb (roughly 1)} :c 2})
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb anything}})
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (roughly 1)}}))

(facts "predicates in matchers"
  (fact "you can't use a plain predicate inside matchers"
    {:a {:bb 1} :c 2} =not=> (ch/match {:a {:bb odd?} :c 2})
    {:a {:bb 1} :c 2} =not=> (ch/embeds {:a {:bb odd?}}))
  (fact "but if you wrap them as matchers you're set"
    {:a {:bb 1} :c 2} => (ch/match {:a {:bb (c/pred->matcher odd?)} :c 2})
    {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (c/pred->matcher odd?)}}))
  (fact "or if you wrap them as midje checkers you're also set"
    {:a {:bb 1} :c 2} => (ch/match {:a {:bb (as-checker odd?)} :c 2})
    {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (as-checker odd?)}})))

(defrecord Point [x y])

(fact "matching records with maps"
  (->Point 1 2) => (ch/match {:x 1 :y 2})
  {:a (->Point 1 2) :b 2} => (ch/embeds {:a (->Point 1 2)})
  {:a (->Point 1 2) :b 2} => (ch/embeds {:a {:x 1 :y 2}})
  (->Point {:a 1} {:b 2}) => (ch/embeds {:x {:a 1}})
  (->Point {:a [1 2]} {:b 2}) => (ch/embeds {:x {:a (c/in-any-order [2 1])}})
  (->Point {:a 1 :c 3} {:b 2}) =not=> (ch/embeds {:x {:a 1 :c 6}}))
