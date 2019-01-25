(ns matcher-combinators.test
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [clojure.string :as str]
            [clojure.test :as clojure.test]))

(defn- stacktrace-file-and-line
  [stacktrace]
  (if (seq stacktrace)
    (let [^StackTraceElement s (first stacktrace)]
      {:file (.getFileName s) :line (.getLineNumber s)})
    {:file nil :line nil}))

(defn- core-or-this-class-name? [^StackTraceElement stacktrace]
  (let [cl-name (.getClassName stacktrace)]
    (or (str/starts-with? cl-name "matcher_combinators.test$")
        (str/starts-with? cl-name "java.lang."))))

;; had to include this from `clojure.test` because there is no good way to run
;; this logic when not reporting a `:fail` or `:error`.
(defn with-file+line-info [report]
  (->> (.getStackTrace (Thread/currentThread))
       (drop-while core-or-this-class-name?)
       stacktrace-file-and-line
       (merge report)))

(defmethod clojure.test/assert-expr 'match? [msg form]
  `(let [[matcher# actual#] (list ~@(rest form))]
     (if (core/matcher? matcher#)
       (let [result# (core/match matcher# actual#)]
         (clojure.test/do-report
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
       (clojure.test/do-report
        {:type     :fail
         :message  ~msg
         :expected '~form
         :actual   (str "The second argument of match? isn't a matcher")}))))

(defmethod clojure.test/assert-expr 'thrown-match? [msg form]
  ;; (is (thrown-with-match? exception-class matcher expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the exception data satisfies the provided matcher.
  (let [klass   (nth form 1)
        matcher (nth form 2)
        body    (nthnext form 3)]
    `(try ~@body
          (clojure.test/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
          (println 1)
          (catch ~klass e#
            (let [result# (core/match ~matcher (ex-data e#))]
              (clojure.test/do-report
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

(defmethod clojure.test/report :matcher-combinators/exception-mismatch [m]
  (clojure.test/with-test-out
    (clojure.test/inc-report-counter :fail)
    (println "\nFAIL in" (clojure.test/testing-vars-str m))
    (when (seq clojure.test/*testing-contexts*)
      (println (clojure.test/testing-contexts-str)))
    (when-let [message (:message m)]
      (println message))
    (println (:ex-class m) "data mismatches:")
    (printer/pretty-print (:markup m))))

(defmethod clojure.test/report :matcher-combinators/mismatch [m]
  (clojure.test/with-test-out
    (clojure.test/inc-report-counter :fail)
    (println "\nFAIL in" (clojure.test/testing-vars-str m))
    (when (seq clojure.test/*testing-contexts*)
      (println (clojure.test/testing-contexts-str)))
    (when-let [message (:message m)]
      (println message))
    (println "mismatch:")
    (printer/pretty-print (:markup m))))
