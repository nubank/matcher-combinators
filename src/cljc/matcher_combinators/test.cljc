(ns matcher-combinators.test
  "Integration with clojure.test or cljs.test (depending on which platform
  you're running on)."
  (:require
    [matcher-combinators.dispatch]
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


     `(defmethod clojure.test/assert-expr 'baz? [msg form]
     (build-match-assert 'baz? {java.lang.Long greater-than-matcher} msg form))`"
     matcher-combinators.clj-test/build-match-assert))
