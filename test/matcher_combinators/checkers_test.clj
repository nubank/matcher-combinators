(ns matcher-combinators.checkers-test
  (:require [midje.sweet :as m :refer [fact facts future-fact]]
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
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/match {:a [{:bb 1} {:cc 2 :dd 3}] :b 4}))

(future-fact "nuanced map in a sequence in a map behavior"
  ;; Should the following 2 tests pass?
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2}]})
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/embeds {:a [{:bb 1} {:cc 2}] :b 4}))

(fact "use midje checkers inside matchers"
  {:a {:bb 1} :c 2} => (ch/match m/anything)
  {:a {:bb 1} :c 2} => (ch/embeds m/anything)
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb m/anything} :c 2})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb (m/roughly 1)} :c 2})
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb m/anything}})
  {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (m/roughly 1)}}))

(facts "predicates in matchers"
  (fact "you can't use a plain predicate inside matchers"
    {:a {:bb 1} :c 2} =not=> (ch/match {:a {:bb odd?} :c 2})
    {:a {:bb 1} :c 2} =not=> (ch/embeds {:a {:bb odd?}}))
  (fact "but if you wrap them as matchers you're set"
    {:a {:bb 1} :c 2} => (ch/match {:a {:bb (c/pred->matcher odd?)} :c 2})
    {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (c/pred->matcher odd?)}}))
  (fact "or if you wrap them as midje checkers you're also set"
    {:a {:bb 1} :c 2} => (ch/match {:a {:bb (m/as-checker odd?)} :c 2})
    {:a {:bb 1} :c 2} => (ch/embeds {:a {:bb (m/as-checker odd?)}})))

(defrecord Point [x y])

(fact "matching records with maps"
  (->Point 1 2) => (ch/match {:x 1 :y 2})
  {:a (->Point 1 2) :b 2} => (ch/embeds {:a (->Point 1 2)})
  {:a (->Point 1 2) :b 2} => (ch/embeds {:a {:x 1 :y 2}})
  (->Point {:a 1} {:b 2}) => (ch/embeds {:x {:a 1}})
  (->Point {:a [1 2]} {:b 2}) => (ch/embeds {:x {:a (c/in-any-order [2 1])}})
  (->Point {:a 1 :c 3} {:b 2}) =not=> (ch/embeds {:x {:a 1 :c 6}}))

;; TODO PLM: do we want to allow this?:
(fact "matching maps with records"
  {:x 1 :y 2} => (ch/match (->Point 1 2))
  {:a {:x 1 :y 2}} => (ch/embeds {:a (->Point 1 2)})
  {:x 1 :y 3} =not=> (ch/match (->Point 1 2))
  {:x 1 :z 2} =not=> (ch/match (->Point 1 2)))

(fact "matching records with records"
  (->Point 1 2) => (ch/match (->Point 1 2))
  (->Point 1 2) => (ch/embeds (->Point 1 2)))

(fact "match doesn't coerce like midje `just`"
  {:a 1 :b 2} => (m/just [[:a 1] [:b 2]])
  {:a 1 :b 2} =not=> (ch/match [[:a 1] [:b 2]]))

(future-fact "dealing with sets"
  #{3 8 1} => (m/just [odd? 3 even?])
  ;; When would someone write a test like this:
  ;; and how is the best way to write such tests using matcher-combinators?
  #{3 8 1} =not=> (ch/match [(m/as-checker odd?) 3 (m/as-checker even?)]))

;; TODO PLM: implement this using matchers somehow
(fact [5 1 4 2] => (m/contains [1 2 5] :gaps-ok :in-any-order))
(fact [5 1 4 2] => (ch/match (c/match-subseq [5 1]))
      [5 1 4 2] => (ch/match (c/match-subseq [5 1 4 2]))
      [5 1 4 2] =not=> (ch/match (c/match-subseq [5 1 4 2 6]))
      [5 1 4 2] =not=> (ch/match (c/match-subseq [1 5])))

(fact [5 1 4 2] => (ch/match (c/match-subset [5 1]))
      [5 1 4 2] => (ch/match (c/match-subset [1 5]))
      [5 1 4 2] => (ch/match (c/match-subset [5 1 4 2]))
      [5 1 4 2] => (ch/match (c/match-subset [1 5 2 4]))
      [5 1 4 2] =not=> (ch/match (c/match-subset [5 1 4 2 6])))

(fact "Find optimal in-any-order matching just like midje"
  [1 3] => (m/just [odd? 1] :in-any-order)

  {:a [1 3]} => (ch/match {:a (c/in-any-order [(c/pred->matcher odd?) 1])})
  {:a [1 3]} => (ch/match {:a (c/in-any-order [1 (c/pred->matcher odd?)])})

  {:a [1 3]} => (ch/match {:a (c/in-any-order [(m/as-checker odd?) 1])})
  {:a [1]} =not=> (ch/match {:a (c/in-any-order [(m/as-checker odd?) 1])})
  [1 2] => (ch/match (c/in-any-order [(m/as-checker even?)
                                      (m/as-checker odd?)])))
