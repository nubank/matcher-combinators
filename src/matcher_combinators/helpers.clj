(ns matcher-combinators.helpers)

(defn find-first [pred coll]
  (->> coll (filter pred) first))

(defn permutations
  "Lazy seq of all permutations of a seq"
  [coll]
  (for [i (range 0 (count coll))]
    (lazy-cat (drop i coll) (take i coll))))

(defn extended-fn? [x]
  ;; via suchwow
  (or (fn? x)
      (instance? clojure.lang.MultiFn x)))

