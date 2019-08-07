(ns matcher-combinators.utils)

(defn roughly? [expected actual delta]
  (and (number? actual)
       (>= expected (-' actual delta))
       (<= expected (+' actual delta))))

