(ns matcher-combinators.foo
  (:require [clojure.test :refer [deftest testing is are]]
            [matcher-combinators.parser]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.core :as c]
            [matcher-combinators.test]))

(def x (c/Value. 3))
(def y (m/equals 3))

#?(:cljs
   (enable-console-print!))

(deftest foo-test
  (testing "does it work?"
    (is (match? 3 2))))
