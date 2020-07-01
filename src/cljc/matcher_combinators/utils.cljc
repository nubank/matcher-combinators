(ns matcher-combinators.utils
  "Internal use only. Subject (and likely) to change.")

(defn processable-number? [v]
  #?(:clj (and (number? v)
               (try
                 (and (not (Double/isInfinite v))
                      (not (Double/isNaN v)))
                 (catch Exception _ false)))
     :cljs (and (number? v)
                (not (infinite? v)))))

(defn within-delta? [delta expected actual]
  (and (processable-number? actual)
       (>= expected (- actual (Math/abs delta)))
       (<= expected (+ actual (Math/abs delta)))))

(defn find-first [pred coll]
  (->> coll (filter pred) first))

(defn remove-first
  "Similar to `remove` but stops after removing 1 element"
  [pred coll]
  (let [[x [y & z]] (split-with (complement pred) coll)]
    (concat x z)))
