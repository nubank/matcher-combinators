(ns matcher-combinators.utils
  "Internal use only. Subject (and likely) to change.")

(defn- processable-number? [v]
  #?(:clj (or (decimal? v)
              (and (number? v)
                   (not (Double/isInfinite v))
                   (not (Double/isNaN v))))
     :cljs (and (number? v)
                (not (infinite? v)))))

(defn- abs [n]
  (if (decimal? n)
    (.abs n)
    (Math/abs n)))

(defn within-delta?
  "Internal use only. Subject to change and removal.
  Supports the `within-delta` matcher."
  [delta expected actual]
  (and (processable-number? actual)
       (>= expected (- actual (abs delta)))
       (<= expected (+ actual (abs delta)))))

(defn find-first
  "Internal use only. Subject to change and removal."
  [pred coll]
  (->> coll (filter pred) first))

(defn remove-first
  "Internal use only. Subject to change and removal.
  Similar to `remove` but stops after removing 1 element"
  [pred coll]
  (let [[x [y & z]] (split-with (complement pred) coll)]
    (concat x z)))
