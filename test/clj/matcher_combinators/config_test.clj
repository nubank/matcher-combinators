(ns matcher-combinators.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [colorize.core :as colorize]
            [matcher-combinators.config :as config]
            [matcher-combinators.core :as c]
            [matcher-combinators.printer :as printer]))

(deftest ansi-color-test
  (testing "with color"
    (config/enable-ansi-color!)
    (is (= (str "(unexpected " (colorize/red 1) ")\n")
           (printer/as-string (list 'unexpected (printer/->ColorTag :red 1))))))
  (testing "disable coloring"
    (config/disable-ansi-color!)
    (is (= (str "(unexpected 1)\n")
           (printer/as-string (list 'unexpected (printer/->ColorTag :red 1))))))
    (config/enable-ansi-color!))

(deftest redact-matched-output-test
  (matcher-combinators.config/disable-redaction!)
  (is (= (str "[1\n 2\n {:a 2,\n  :b [4 (mismatch (expected " (colorize/yellow 5) ") (actual " (colorize/red 6) "))],\n  :c [2 [3 4]]}]\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match [1 2 {:a 2 :b [4 5] :c [2 [3 4]]}]
                      [1 2 {:a 2 :b [4 6] :c [2 [3 4]]}])))))

  (matcher-combinators.config/enable-redaction!)
  (is (= (str "[{:b [(mismatch (expected " (colorize/yellow 5) ") (actual " (colorize/red 6) "))]}]\n")
         (printer/as-string
           (:matcher-combinators.result/value
             (c/match [1 2 {:a 2 :b [4 5] :c [2 [3 4]]}]
                      [1 2 {:a 2 :b [4 6] :c [2 [3 4]]}]))))))
