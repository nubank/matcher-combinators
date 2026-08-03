(ns matcher-combinators.test
  "Integration with clojure.test or cljs.test (depending on which platform
  you're running on).

  This namespace provides useful placeholder
  vars for match?, match-with?, thrown-match? and match-roughly?;
  the placeholders are nil (the actual implementations are extended
  via the clojure.test/assert-expr multimethod), but importing these will prevent
  linters from flagging otherwise undefined names.

  Even if not concerned about linting, it is necessary to have
  some namespace require matcher-combinators.test to ensure that
  match? and friends can be used within clojure.test/is.

  Commonly, a dev-only user namespace will require this namespace."
  (:require
   #?(:cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer        [is are deftest testing]])
   #?(:cljs [matcher-combinators.cljs-test]
      :clj  [matcher-combinators.clj-test])))

(defn match?
  "Asserts that the `actual` matches the `expected`, where the `expected` can be a value, a predicate function, or a matcher-combinator.

(is (match? [0 1 2] (range 3)))
(is (match? (complement empty?) (range 3)))
(is (match? (matcher-combinators.matchers/in-any-order [zero? odd? even?]) (range 3)))"
  [matcher actual]
  (throw (#?(:cljs js/Error. :clj IllegalArgumentException.)
          "`match?` must be used inside of `clojure.test/is`")))

(defn match-with?
  {:deprecated "3.0.0"
   :doc "DEPRECATED: Use (match? (matcher-combinators.matchers/match-with <type->matcher> <expected>) <actual>) instead."}
  [type->matcher matcher actual]
  (throw (#?(:cljs js/Error. :clj IllegalArgumentException.)
          "`match-with?` must be used inside of `clojure.test/is`")))

(defn thrown-match?
  "Asserts that evaluating expr throws an exception where the exception's ex-data satisfies the provided matcher.

2-arity: (is (thrown-match? matcher expr))
3-arity: (is (thrown-match? exception-class matcher expr))"
  ([matcher actual]
   (throw (#?(:cljs js/Error. :clj IllegalArgumentException.)
           "`thrown-match?` must be used inside of `clojure.test/is`")))
  ([exception-class matcher actual]
   (throw (#?(:cljs js/Error. :clj IllegalArgumentException.)
           "`thrown-match?` must be used inside of `clojure.test/is`"))))

(defn match-roughly?
  {:deprecated "3.0.0"
   :doc "DEPRECATED: Instead use (match? (matcher-combinators.matchers/match-with [number? (matcher-combinators.matchers/within-delta 0.01M)] <expected>) <actual>)"}
  [delta matcher actual]
  (throw (#?(:cljs js/Error. :clj IllegalArgumentException.)
          "`match-roughly?` must be used inside of `clojure.test/is`")))

#?(:clj
   (def build-match-assert
     "Allows you to define a custom clojure.test match assert:

     `(defmethod clojure.test/assert-expr 'abs-value? [msg form]
     (build-match-assert 'abs-value? [int? abs-value-matcher] msg form))`"
     matcher-combinators.clj-test/build-match-assert))
