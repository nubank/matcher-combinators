(ns matcher-combinators.result
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]))

(s/def ::weight nat-int?)
(s/def ::type #{:mismatch :match})
(s/def ::value s/any?)

(s/def ::result (s/keys :req [::weight ::type ::annotated-result]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helpers

(defn assert-spec [spec value]
  (when-not (s/valid? spec value)
    (throw (AssertionError. (expound/expound-str spec value)))))
