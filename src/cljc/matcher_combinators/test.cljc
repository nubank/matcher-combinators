(ns matcher-combinators.test
  (:require
    [matcher-combinators.dispatch]
    #?(:cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
       :clj  [clojure.test :as t :refer        [is are deftest testing]])
    #?(:cljs [matcher-combinators.cljs-test]
       :clj  [matcher-combinators.clj-test])))

(declare match?)
(declare match-with?)
(declare thrown-match?)

#?(:clj
   (def build-match-assert
     "Allows you to define a custom clojure.test match assert:


     `(defmethod clojure.test/assert-expr 'baz? [msg form]
     (build-match-assert 'baz? {java.lang.Long greater-than-matcher} msg form))`"
     matcher-combinators.clj-test/build-match-assert))
