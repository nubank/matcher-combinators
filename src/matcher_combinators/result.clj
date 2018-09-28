(ns matcher-combinators.result
  (:require [clojure.spec.alpha :as s]))

;; the weight of the mismatch. `0` is a match, and any number above is the
;; number of leaf matchers that mismatch
(s/def ::weight nat-int?)

(s/def ::type #{:mismatch :match})

;; either the original value, when matching, or the value with mismatches
;; annotated
(s/def ::value any?)

(s/def ::result (s/keys :req [::weight ::type ::annotated-result]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helpers

(defn assert-spec [spec value]
  (when-not (s/valid? spec value)
    (throw (AssertionError. (s/explain-data spec value)))))
