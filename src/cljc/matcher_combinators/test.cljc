(ns matcher-combinators.test
  "Integration with clojure.test or cljs.test (depending on which platform
  you're running on).

  This namespace provides useful placeholder
  vars for match?, match-with?, thrown-match? and match-roughly?;
  the placeholders are macros that throw an error if used improperly
  (the actual implementations are extended via the
  clojure.test/assert-expr multimethod), but importing these will prevent
  linters from flagging otherwise undefined names.

  Even if not concerned about linting, it is necessary to have
  some namespace require matcher-combinators.test to ensure that
  match? and friends can be used withing clojure.test/is.

  Commonly, a dev-only user namespace will require this namespace."
  (:require
   #?(:cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
       :clj  [clojure.test :as t :refer        [is are deftest testing]])
    #?(:cljs [matcher-combinators.cljs-test]
       :clj  [matcher-combinators.clj-test])))

(defn- bad-usage [expr-name]
  `(throw (#?(:clj IllegalArgumentException.
              :cljs js/Error.)
           ~(str expr-name " must be used inside `is`."))))

(defmacro match?
  "Check `actual` with the provided `matcher`.

  If `matcher` is a scalar or collection type except regex or map, uses the built-in matcher `equals`:

  * For scalars, `matcher` is compared directly with `actual`.
  * For sequences, `matcher` specifies count and order of matching elements. The elements, themselves, are matched based on their types or predicates.
  * For sets, `matcher` specifies count of matching elements. The elements, themselves, are matched based on their types or predicates.

  ```clojure
  (is (match? 37 (+ 29 8)))
  (is (match? \"this string\" (str \"this\" \" \" \"string\")))
  (is (match? :this/keyword (keyword \"this\" \"keyword\")))

  (is (match? [1 3] [1 3]))
  (is (match? [1 odd?] [1 3]))
  (is (match? [#\"red\" #\"violet\"] [\"Roses are red\" \"Violets are ... violet\"]))
  ;; use `m/prefix` when you only care about the first n items
  (is (match? (m/prefix [odd? 3]) [1 3 5]))
  ;; use `m/in-any-order` when order doesn't matter
  (is (match? (m/in-any-order [odd? odd? even?]) [1 2 3]))

  (is (match? #{1 2 3} #{3 2 1}))
  (is (match? #{odd? even?} #{1 2}))
  ;; use `m/set-equals` to repeat predicates
  (is (match? (m/set-equals [odd? odd? even?]) #{1 2 3}))
  ```

  If `matcher` is a regex, uses the built-in matcher `regex` (matches using `(re-find matcher actual)`):

  ```clojure
  (is (match? #\"fox\" \"The quick brown fox jumps over the lazy dog\"))
  ```

  If `matcher` is a map, uses the built-in matcher `embeds` (matches when `actual` contains some of the same key/values as `matcher`):

  ```clojure
  (is (match? {:name/first \"Alfredo\"}
              {:name/first  \"Alfredo\"
               :name/last   \"da Rocha Viana\"
               :name/suffix \"Jr.\"}))
  ```

  Otherwise, `matcher` must be a matcher (implements the Matcher protocol)."
  [matcher actual]
  (bad-usage "match?"))

(defmacro thrown-match?
  "Asserts that evaluating `expr` throws an `exception-class`.
  Also asserts that the exception data satisfies the provided `matcher`.

  Defaults to `clojure.lang.ExceptionInfo` if `exception-class` is not provided.

  ```clojure
  (is (thrown-match? {:foo 1}
                     (throw (ex-info \"Boom!\" {:foo 1 :bar 2}))))

  (is (thrown-match? clojure.lang.ExceptionInfo
                     {:foo 1}
                     (throw (ex-info \"Boom!\" {:foo 1 :bar 2}))))
  ```"
  ([matcher expr] `(thrown-match? nil ~matcher ~expr))
  ([exception-class matcher expr]
   (bad-usage "thrown-match?")))

(defmacro ^:deprecated match-with?
  "DEPRECATED: `match-with?` is deprecated. Use `(match? (matchers/match-with <type->matcher> <expected>) <actual>)` instead."
  [type->matcher matcher actual]
  (bad-usage "match-with?"))

(defmacro ^:deprecated match-equals?
  "DEPRECATED: `match-equals?` is deprecated. Use `(match? (matchers/match-with [map? matchers/equals] <expected>) <actual>)` instead."
  [matcher actual]
  (bad-usage "match-equals?"))

(defmacro ^:deprecated match-roughly?
  "DEPRECATED: `match-roughly?` is deprecated. Use `(match? (matchers/within-delta <expected>) <actual>)` instead."
  [delta matcher actual]
  (bad-usage "match-roughly?"))

#?(:clj
   (def build-match-assert
     "Allows you to define a custom clojure.test match assert:

     `(defmethod clojure.test/assert-expr 'abs-value? [msg form]
     (build-match-assert 'abs-value? [int? abs-value-matcher] msg form))`"
     matcher-combinators.clj-test/build-match-assert))
