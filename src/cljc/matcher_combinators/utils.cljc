(ns ^:no-doc matcher-combinators.utils
  "Internal use only. Subject (and likely) to change."
  (:refer-clojure :exclude [abs]))

(defn- processable-number? [v]
  #?(:clj (or (decimal? v)
              (and (number? v)
                   (not (Double/isInfinite v))
                   (not (Double/isNaN v))))
     :cljs (and (number? v)
                (not (infinite? v)))))

(defn- abs
  "Returns the abs value of n.

  Note that clojure-1.11.0 adds abs to core, however consumers may or
  may not be running with clojure 1.11.0 and we don't want to force
  that dependency (at least not just a week after the release of
  1.11.0)."
  [n]
  #?(:clj (if-let [core-abs (resolve 'clojure.core/abs)]
            (core-abs n)
            (if (decimal? n)
              (.abs n)
              (Math/abs n)))
     :cljs (js/Math.abs n)))

(defn ^:no-doc within-delta?
  "Internal use only. Subject to change and removal.
  Supports the `within-delta` matcher."
  [delta expected actual]
  (and (processable-number? actual)
       (>= expected (- actual (abs delta)))
       (<= expected (+ actual (abs delta)))))

(defn ^:no-doc find-first
  "Internal use only. Subject to change and removal."
  [pred coll]
  (->> coll (filter pred) first))

(defn ^:no-doc remove-first
  "Internal use only. Subject to change and removal.
  Similar to `remove` but stops after removing 1 element"
  [pred coll]
  (let [[x [y & z]] (split-with (complement pred) coll)]
    (concat x z)))
