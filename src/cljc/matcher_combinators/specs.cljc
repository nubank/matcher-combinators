(ns matcher-combinators.specs
  (:require [clojure.spec.alpha :as s]
            [matcher-combinators.core :as core]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.result :as result]))

(s/def :match/result    ::result/type)
(s/def :mismatch/detail ::result/value)
(s/def ::standalone-match-result
  (s/keys :req [:match/result]
          :opt [:mismatch/detail]))

(s/def :matcher-combinators/match-args
  (s/alt :match-result        (s/alt :standalone ::standalone-match-result
                                     :core       ::result/result)
         :expected-and-actual (s/cat :expected (fn [v] (satisfies? core/Matcher v))
                                     :actual   any?)))

(s/fdef matcher-combinators.core/match?
  :args :matcher-combinators/match-args
  :ret boolean?)

(s/fdef matcher-combinators.standalone/match?
  :args :matcher-combinators/match-args
  :ret boolean?)

(comment

  (def args-spec)

  (s/valid? :matcher-combinators/match-args [1 2])
  (s/valid? :matcher-combinators/match-args [{:match/result :match}])
  (s/valid? :matcher-combinators/match-args [{:match/result :mismatch}])
  (s/valid? :matcher-combinators/match-args [{:match/result :mismatch} {:match/result :mismatch}])
  (s/valid? :matcher-combinators/match-args [{:match/result :mismatch} {:a :b}])
  )
