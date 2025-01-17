(ns matcher-combinators.config-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [colorize.core :as colorize]
            [matcher-combinators.config :as config]
            [matcher-combinators.core :as c]
            [matcher-combinators.printer :as printer]
            matcher-combinators.test))

(defn set-config-defaults! []
  (config/enable-ansi-color!)
  (config/disable-abbreviation!))

(use-fixtures :each
              (fn [t]
                (set-config-defaults!)
                (t)
                (set-config-defaults!)))

(deftest ansi-color-test
  (testing "with color"
    (is (= (str "(unexpected " (colorize/red 1) ")\n")
           (printer/as-string (list 'unexpected (printer/->ColorTag :red 1))))))
  (testing "disable coloring"
    (config/disable-ansi-color!)
    (is (= (str "(unexpected 1)\n")
           (printer/as-string (list 'unexpected (printer/->ColorTag :red 1)))))))

(deftest abbreviated-matched-output-test
  (config/disable-abbreviation!)
  (is (= (str "[1\n 2\n {:a 2,\n  :b [4 (mismatch (expected " (colorize/yellow 5) ") (actual " (colorize/red 6) "))],\n  :c [2 [3 4]]}]\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match [1 2 {:a 2 :b [4 5] :c [2 [3 4]]}]
                      [1 2 {:a 2 :b [4 6] :c [2 [3 4]]}])))))

  (config/enable-abbreviation!)
  (is (= (str "{:stuff [{:b [(mismatch (expected " (colorize/yellow 5) ") (actual " (colorize/red 6) "))]}],\n ... }\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match {:stuff [1 2 {:a 2 :b [4 5] :c [2 [3 4]]}]}
                      {:stuff [1 2 {:a 2 :b [4 6] :c [2 [3 4]]}]})))))

  (is (= (str "[{:b [(mismatch (expected " (colorize/yellow 5) ") (actual " (colorize/red 6) "))]} ...]\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match [1 2 {:a 2 :b [4 5] :c [2 [3 4]]}]
                      [1 2 {:a 2 :b [4 6] :c [2 [3 4]]}])))))
  (config/disable-abbreviation!))

(deftest abbreviated-set-output-test
  (config/disable-abbreviation!)
  (is (= (str "[#{(unexpected " (colorize/red 1) ") 2}]\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match [#{2}] [#{1 2}])))))

  (config/enable-abbreviation!)
  (is (= (str "[#{(unexpected " (colorize/red 1) ")} ...]\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match [#{2}] [#{1 2}])))))
  (config/disable-abbreviation!))
