(ns matcher-combinators.helpers)

(defn find-first [pred coll]
  (->> coll (filter pred) first))

(defn remove-first
  "Similar to `remove` but stops after removing 1 element"
  [pred coll]
  (let [[x [y & z]] (split-with (complement pred) coll)]
    (concat x z)))
