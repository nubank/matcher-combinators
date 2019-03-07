(ns matcher-combinators.cljs-test
  #?(:cljs
     (:require-macros [matcher-combinators.cljs-test]))
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [cljs.test :as t :refer [assert-expr] :refer-macros [deftest is]]))

(defn with-file+line-info [report]
  #?(:cljs (merge (t/file-and-line (js/Error.) 4) report)))

#?(:clj (do
(defmethod assert-expr 'match? [_ msg form]
  `(let [[matcher# actual#] (list ~@(rest form))]
     (if (core/matcher? matcher#)
       (let [result# (core/match matcher# actual#)]
         (t/do-report
          (if (core/match? result#)
            {:type     :pass
             :message  ~msg
             :expected '~form
             :actual   (list 'match matcher# actual#)}
            (do
            (with-file+line-info
              {:type     :matcher-combinators/mismatch
               :message  ~msg
               :expected '~form
               :actual   (list '~'not (list 'match matcher# actual#))
               :markup   (::result/value result#)})))))
       (t/do-report
        {:type     :fail
         :message  ~msg
         :expected '~form
         :actual   (str "The first argument of match? isn't a matcher")}))))

(defmethod assert-expr 'thrown-match? [_ msg form]
  ;; (is (thrown-with-match? exception-class matcher expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the exception data satisfies the provided matcher.
  (let [klass   (nth form 1)
        matcher (nth form 2)
        body    (nthnext form 3)]
    `(try ~@body
          (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
          (println 1)
          (catch ~klass e#
            (let [result# (core/match ~matcher (ex-data e#))]
              (t/do-report
               (if (core/match? result#)
                 {:type     :pass
                  :message  ~msg
                  :expected '~form
                  :actual   (list 'thrown-match? ~klass ~matcher '~body)}
                 (with-file+line-info
                   {:type     :matcher-combinators/exception-mismatch
                    :message  ~msg
                    :expected '~form
                    :actual   (list '~'not (list 'thrown-match? ~klass ~matcher '~body))
                    :ex-class ~klass
                    :markup   (::result/value result#)}))))
            e#))))))

#?(:cljs (do

(defmethod t/report [::t/default :matcher-combinators/exception-mismatch] [m]
  (t/inc-report-counter! :fail)
  (println "\nFAIL in" (t/testing-vars-str m))
  (when (seq t/*testing-contexts*)
    (println (t/testing-contexts-str)))
  (when-let [message (:message m)]
    (println message))
  (println (:ex-class m) "data mismatches:")
  (printer/pretty-print (:markup m)))

(defmethod t/report [::t/default :matcher-combinators/mismatch] [m]
  (t/inc-report-counter! :fail)
  (println "\nFAIL in" (t/testing-vars-str m))
  (when (seq t/*testing-contexts*)
    (println (t/testing-contexts-str)))
  (when-let [message (:message m)]
    (println message))
  (println "mismatch:")
  (printer/pretty-print (:markup m)))))
