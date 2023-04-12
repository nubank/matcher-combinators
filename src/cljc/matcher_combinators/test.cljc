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
  match? and friends can be used withing clojure.test/is.

  Commonly, a dev-only user namespace will require this namespace."
  (:require
   #?(:cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
       :clj  [clojure.test :as t :refer        [is are deftest testing]])
    #?(:cljs [matcher-combinators.cljs-test]
       :clj  [matcher-combinators.clj-test])))

(declare ^{:arglists '([matcher actual])}
         match?)
(declare ^{:arglists '([type->matcher matcher actual])}
         match-with?)
(declare ^{:arglists '([matcher actual]
                       [exception-class matcher actual])}
         thrown-match?)
(declare ^{:arglists '([delta matcher actual])}
         match-roughly?)

#?(:clj
   (def build-match-assert
     "Allows you to define a custom clojure.test match assert:

     `(defmethod clojure.test/assert-expr 'abs-value? [msg form]
     (build-match-assert 'abs-value? [int? abs-value-matcher] msg form))`"
     matcher-combinators.clj-test/build-match-assert))
