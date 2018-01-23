(ns matcher-combinators.midje-test
  (:require [midje.sweet :as midje :refer [fact facts => falsey]]
            [matcher-combinators.midje :as ch]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.core :as c]
            [midje.emission.api :as emission]))

(fact "sequence matching"
  [] => (ch/match [])
  [1] => (ch/match [1])
  [[1]] => (ch/match [[1]])
  [[[1]]] => (ch/match [[[1]]])
  [[[1]]] => (ch/match [[[odd?]]]))

(fact "map matching"
  {:a {:bb 1} :c 2} => (ch/match (m/equals-map {:a {:bb 1} :c 2}))
  {:a {:bb 1} :c 2} => (ch/match (m/equals-map {:a {:bb odd?} :c 2})))

(fact "map embeds"
  {:a {:aa 11 :bb {:aaa 111}} :b 2} => (ch/match {:a {:bb {:aaa 111}}})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb 1}})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb odd?}}))

(fact "map in a sequence in a map"
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/match {:a [{:bb 1} {:cc 2 :dd 3}] :b 4})
  {:a [{:bb 1} {:cc 2 :dd 3}] :b 4} => (ch/match (m/equals-map {:a [{:bb 1} {:cc 2 :dd 3}] :b 4})))

(fact "use midje checkers inside matchers"
  {:a {:bb 1} :c 2} => (ch/match (m/equals-map {:a {:bb midje/anything} :c 2}))
  {:a {:bb 1} :c 2} => (ch/match (m/equals-map {:a {:bb (midje/roughly 1)} :c 2}))
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb midje/anything}})
  {:a {:bb 1} :c 2} => (ch/match {:a {:bb (midje/roughly 1)}}))

(facts "predicates in matchers"
  (fact "you can use a plain predicate inside matchers"
    {:a {:bb 1} :c 2} => (ch/match (m/equals-map {:a {:bb odd?} :c 2}))
    {:a {:bb 1} :c 2} => (ch/match {:a {:bb odd?}}))
  (fact "but if you want to check exact functions use `equals-value` which
        works like midje's 'exactly'"
    {:a {:bb odd?} :c 2} =not=> (ch/match (m/equals-map {:a {:bb odd?} :c 2}))
    {:a {:bb odd?} :c 2} => (ch/match (m/equals-map {:a {:bb (m/equals-value odd?)} :c 2}))
    {:a {:bb odd?} :c 2} => (ch/match (m/equals-map {:a {:bb (midje/exactly odd?)} :c 2}))))

(defrecord Point [x y])

(fact "matching records with maps"
  (->Point 1 2) => (ch/match (m/equals-map {:x 1 :y 2}))
  {:a (->Point 1 2) :b 2} => (ch/match {:a (->Point 1 2)})
  {:a (->Point 1 2) :b 2} => (ch/match {:a {:x 1 :y 2}})
  (->Point {:a 1} {:b 2}) => (ch/match {:x {:a 1}})
  (->Point {:a [1 2]} {:b 2}) => (ch/match {:x {:a (m/in-any-order [2 1])}})
  (->Point {:a 1 :c 3} {:b 2}) =not=> (ch/match {:x {:a 1 :c 6}}))

;; TODO PLM: do we want to allow this?:
(fact "matching maps with records"
  {:x 1 :y 2} => (ch/match (m/equals-map (->Point 1 2)))
  {:a {:x 1 :y 2}} => (ch/match {:a (->Point 1 2)})
  {:x 1 :y 3} =not=> (ch/match (m/equals-map (->Point 1 2)))
  {:x 1 :z 2} =not=> (ch/match (m/equals-map (->Point 1 2))))

(fact "matching records with records"
  (->Point 1 2) => (ch/match (m/equals-map (->Point 1 2)))
  (->Point 1 2) => (ch/match (->Point 1 2)))

(fact "equals-map doesn't coerce like midje `just`"
  {:a 1 :b 2} => (midje/just [[:a 1] [:b 2]])
  {:a 1 :b 2} =not=> (ch/match (m/equals-seq [[:a 1] [:b 2]])))

(facts "dealing with sets"
 (fact "midje set checker example"
    #{3 8 1} => (midje/just [odd? 3 even?]))
 (fact "to match sets, you need to turn it into a list"
  #{3 8 1} =not=> (ch/match (m/equals-seq [(midje/as-checker odd?) 3 (midje/as-checker even?)]))
  (seq #{3 8 1}) => (ch/match (m/in-any-order [(midje/as-checker odd?) 3 (midje/as-checker even?)]))))

(fact [5 1 4 2] => (midje/contains [1 2 5] :gaps-ok :in-any-order))
(fact [5 1 4 2] => (ch/match (m/sublist [5 1]))
      [5 1 4 2] => (ch/match (m/sublist [5 1 4 2]))
      [5 1 4 2] =not=> (ch/match (m/sublist [5 1 4 2 6]))
      [5 1 4 2] =not=> (ch/match (m/sublist [1 5])))

(fact [5 1 4 2] => (ch/match (m/subset [5 1]))
      [5 1 4 2] => (ch/match (m/subset [1 5]))
      [5 1 4 2] => (ch/match (m/subset [5 1 4 2]))
      [5 1 4 2] => (ch/match (m/subset [1 5 2 4]))
      [5 1 4 2] => (ch/match (m/subset [odd? even?]))
      [5 1 4 2] =not=> (ch/match (m/subset [5 1 4 2 6])))

(fact "Find optimal in-any-order matching just like midje"
  [1 3] => (midje/just [odd? 1] :in-any-order)

  {:a [1 3]} => (ch/match (m/equals-map {:a (m/in-any-order [odd? 1])}))
  {:a [1 3]} => (ch/match (m/equals-map {:a (m/in-any-order [1 odd?])}))

  {:a [1 3]} => (ch/match (m/equals-map {:a (m/in-any-order [(midje/as-checker odd?) 1])}))
  {:a [1]} =not=> (ch/match (m/equals-map {:a (m/in-any-order [(midje/as-checker odd?) 1])}))
  [1 2] => (ch/match (m/in-any-order [(midje/as-checker even?)
                                      (midje/as-checker odd?)])))

(midje/unfinished f)
(let [short-list (ch/match (m/equals-seq [int? int? int?]))]
  (fact "using defined matchers in provided statements"
    (f [1 2 3]) => 1
    (provided
      (f short-list) => 1))

  (fact "using inline matchers in provided statements"
    (fact "succeeding"
      (f [1 2 3]) => 1
      (provided
        (f (ch/match (m/equals-seq [int? int? int?]))) => 1))

    (fact "a match failure will fail the test"
      (emission/silently
        (fact "will fail"
          (f [1 2 :not-this]) => 1
          (provided
            (f (ch/match (m/equals-seq [int? int? int?]))) => 1)))
      => falsey)))


(def now (java.time.LocalDateTime/now))
(def an-id-string "67b22046-7e9f-46b2-a3b9-e68618242864")
(def an-id (java.util.UUID/fromString an-id-string))
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

(fact "match nested maps with UUID, LocalDateTime, BigDecimal, and Double values"
  nested-map
  => (ch/match {:id {:type :user-id
                     :value an-id-string}
                :input {:id {:type keyword?
                             :value an-id-string}
                        :timestamp now}
                :model "sampa_v3"
                :output {:sampa-score 123.4M
                         :user-id another-id
                         :w-alpha -0.123}
                :response-time response-time
                :version string?}))

(def an-object (Object.))
(fact "Objects aren't matchers, so matching on them shouldn't work and produce
       an informative error"
  an-object => (ch/match (m/equals-value an-object))
  an-object =not=> (ch/match an-object)
  (Object.) =not=> (ch/match (Object.)))
