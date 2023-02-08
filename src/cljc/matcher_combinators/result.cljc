(ns matcher-combinators.result)

;; a match result contains a ::value, ::type, and ::weight
;; where
;; - ::value is either the original value, when matching, or the value with any
;;           mismatches annotated
;; - ::weight is the weight of the mismatch. `0` is a match, and any number
;;            above is the number of leaf matchers that mismatch
;; - ::type is either :mismatch or :match
