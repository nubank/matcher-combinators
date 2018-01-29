(ns matcher-combinators.helpers)

(defn find-first [pred coll]
  (->> coll (filter pred) first))

(defn permutations
  "Lazy seq of all permutations of a seq"
  [coll]
  (for [i (range 0 (count coll))]
    (lazy-cat (drop i coll) (take i coll))))

(defn remove-first
  "Similar to `remove` but stops after removing 1 element"
  [pred coll]
  (let [[x [y & z]] (split-with (complement pred) coll)]
    (concat x z)))
