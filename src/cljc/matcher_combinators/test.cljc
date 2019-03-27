(ns matcher-combinators.test
  (:require
    #?(:cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
       :clj  [clojure.test :as t :refer        [is are deftest testing]])
    #?(:cljs [matcher-combinators.cljs-test]
       :clj  [matcher-combinators.clj-test])))

(declare match?)
