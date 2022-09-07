(ns matcher-combinators.parser-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.parser :as parser])
  (:import [java.net URI]))

(def gen-big-decimal
  (gen/fmap (fn [[integral fractional]]
              (BigDecimal. (str integral "." fractional)))
            (gen/tuple gen/small-integer gen/nat)))

(def gen-big-int
  (gen/fmap #(* 1N %) gen/small-integer))

(def gen-java-integer
  (gen/fmap #(Integer. %) gen/small-integer))

(def gen-float
  (gen/fmap #(float %) gen/small-integer))

(def gen-short
  (gen/fmap short gen/small-integer))

(def gen-var (gen/elements (vals (ns-interns 'clojure.core))))

(def query-gen
  (gen/one-of [(gen/return nil) gen/string-alphanumeric]))

(def gen-uri
  ;; well actually generates a URL, but oh well
  (let [scheme          (gen/elements #{"http" "https"})
        authority       (gen/elements #{"www.foo.com" "www.bar.com:80"})
        path            (gen/one-of [(gen/return nil)
                                     (gen/fmap #(str "/" %) gen/string-alphanumeric)])
        args-validation (fn [[_scheme authority path query fragment]]
                          (not (or ;; a URI with just a scheme is invalid
                                (every? nil? (list authority path query fragment))
                                ;; a URI with just a scheme and fragment is invalid
                                (and (not (nil? fragment))
                                     (every? nil? (list authority path query))))))]

    (gen/fmap
     (fn [[scheme authority path query fragment]] (URI. scheme authority path query fragment))
     (gen/such-that
      args-validation
      (gen/tuple scheme authority path query-gen query-gen)))))

(def gen-scalar (gen/one-of [gen-java-integer
                             gen/small-integer ;; really a long
                             gen-short
                             gen/string
                             gen/symbol
                             gen-float
                             gen/double
                             gen/symbol-ns
                             gen/keyword
                             gen/boolean
                             gen/ratio
                             gen/uuid
                             gen-uri
                             gen-big-decimal
                             gen-big-int
                             gen/char
                             gen/bytes
                             gen-var]))

(defn gen-distinct-pair [element-generator]
  (gen/such-that (fn [[i j]] (not= i j)) (gen/tuple element-generator)))

(def gen-scalar-pair
  (gen-distinct-pair gen-scalar))

(defspec test-scalars
  (testing "scalar values act as equals matchers"
    (prop/for-all [i gen-scalar]
      (= (core/match i i)
         (core/match (m/equals i) i)))

    (prop/for-all [[i j] gen-scalar-pair]
      (= (core/match i j)
         (core/match (m/equals i) j)))))

(deftest test-maps
  (testing "act as equals matcher"
    (is (= (core/match (m/equals {:a (m/equals 10)}) {:a 10})
           (core/match (m/equals {:a 10}) {:a 10})
           (core/match {:a 10} {:a 10})))))

(deftest test-vectors
  (testing "vectors act as equals matchers"
    (is (= (core/match (m/equals [(m/equals 10)]) [10])
           (core/match (m/equals [10]) [10])
           (core/match [10] [10])))))

(deftest test-chunked-seq
  (testing "chunked sequences act as equals matchers"
    (is (core/match (seq [1 2 3]) [10]))))

(deftest test-lists
  (testing "lists act as equals matchers"
    (is (= (core/match (m/equals [(m/equals 10)]) [10])
           (core/match (m/equals '(10)) [10])
           (core/match '(10) [10])))))

(deftest test-nil
  (testing "`nil` is parsed as an equals"
    (is (= (core/match (m/equals nil) nil)
           (core/match nil nil)))))

(deftest test-classes
  (testing "java classes are parsed as an equals"
    (is
     (= (core/match (m/equals java.lang.String) java.lang.String)
        (core/match java.lang.String java.lang.String)))))

(deftest test-object
  (let [an-object (Object.)
        another-object (RuntimeException.)]
    (testing "Objects default to equality matching"
      (is (= (core/match (m/equals an-object)
                         an-object)
             (core/match an-object
                         an-object)))
      (is (= (core/indicates-match? (core/match another-object (Object.)))
             (= another-object (Object.)))))))

(deftest mimic-matcher-macro
  (testing "mimic-matcher uses non-namespaced symbol for `-matcher-for`"
    (is (= '-matcher-for
           (->> (macroexpand-1 `(parser/mimic-matcher matchers/equals Integer))
                butlast
                last
                first))))
  ;; this is a regression test for https://github.com/nubank/matcher-combinators/pull/104
  (testing "mimic-matcher uses non-namespaced symbol for `-match`"
    (is (= '-match
           (-> (macroexpand-1 `(parser/mimic-matcher matchers/equals Integer))
               last
               first)))))
