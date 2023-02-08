(ns matcher-combinators.result)

;; The following is commented out in order to support Clojure 1.8
#_(require '[clojure.spec.alpha :as s])

;; the weight of the mismatch. `0` is a match, and any number above is the
;; number of leaf matchers that mismatch
#_(s/def ::weight nat-int?)

#_(s/def ::type #{:mismatch :match})

;; either the original value, when matching, or the value with mismatches
;; annotated
#_(s/def ::value any?)

#_(s/def ::result (s/keys :req [::weight ::type ::value]))
