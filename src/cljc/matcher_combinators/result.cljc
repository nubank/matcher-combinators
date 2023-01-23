(ns matcher-combinators.result)

;; the weight of the mismatch. `0` is a match, and any number above is the
;; number of leaf matchers that mismatch
#_(s/def ::weight nat-int?)

#_(s/def ::type #{:mismatch :match})

;; either the original value, when matching, or the value with mismatches
;; annotated
#_(s/def ::value any?)

#_(s/def ::result (s/keys :req [::weight ::type ::value]))
