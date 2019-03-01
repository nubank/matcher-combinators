(ns matcher-combinators.test
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.result :as result]
            [clojure.string :as str]
            [cljs.test :as test]))

(defn with-file+line-info [report]
  (merge (test/file-and-line (js/Error.) 4) report))

(defmethod test/assert-expr 'match? [msg form]
  `(let [[matcher# actual#] (list ~@(rest form))]
     (if (core/matcher? matcher#)
       (let [result# (core/match matcher# actual#)]
         (test/do-report
          (if (core/match? result#)
            {:type     :pass
             :message  ~msg
             :expected '~form
             :actual   (list 'match? matcher# actual#)}
            (with-file+line-info
              {:type     :matcher-combinators/mismatch
               :message  ~msg
               :expected '~form
               :actual   (list '~'not (list 'match? matcher# actual#))
               :markup   (::result/value result#)}))))
       (test/do-report
        {:type     :fail
         :message  ~msg
         :expected '~form
         :actual   (str "The second argument of match? isn't a matcher")}))))

(defmethod test/assert-expr 'thrown-match? [msg form]
  ;; (is (thrown-with-match? exception-class matcher expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the exception data satisfies the provided matcher.
  (let [klass   (nth form 1)
        matcher (nth form 2)
        body    (nthnext form 3)]
    `(try ~@body
          (test/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
          (println 1)
          (catch ~klass e#
            (let [result# (core/match ~matcher (ex-data e#))]
              (test/do-report
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
            e#))))

(defmethod test/report :matcher-combinators/exception-mismatch [m]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (println "\nFAIL in" (test/testing-vars-str m))
    (when (seq test/*testing-contexts*)
      (println (test/testing-contexts-str)))
    (when-let [message (:message m)]
      (println message))
    (println (:ex-class m) "data mismatches:")
    (printer/pretty-print (:markup m))))

(defmethod test/report :matcher-combinators/mismatch [m]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (println "\nFAIL in" (test/testing-vars-str m))
    (when (seq test/*testing-contexts*)
      (println (test/testing-contexts-str)))
    (when-let [message (:message m)]
      (println message))
    (println "mismatch:")
    (printer/pretty-print (:markup m))))
