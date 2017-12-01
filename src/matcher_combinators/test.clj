(ns matcher-combinators.test
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [clojure.string :as str]
            [clojure.test :as clojure.test]))

(defn- stacktrace-file-and-line
  [stacktrace]
  (if (seq stacktrace)
    (let [^StackTraceElement s (first stacktrace)]
      {:file (.getFileName s) :line (.getLineNumber s)})
    {:file nil :line nil}))

;; had to include this from `clojure.test` because there is no good way to run
;; this logic when not reporting a `:fail` or `:error`.
(defn- with-file+line-info [report]
  (merge
    report
    (stacktrace-file-and-line (drop-while
                                #(let [cl-name (.getClassName ^StackTraceElement %)]
                                   (or (str/starts-with? cl-name "matcher_combinators.test$")
                                       (str/starts-with? cl-name "java.lang.")))
                                (.getStackTrace (Thread/currentThread))))))

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
               {:type     :mismatch
                :message  ~msg
                :expected '~form
                :actual   (list '~'not (list 'match? matcher# actual#))
                :markup   (second result#)}))))
       (clojure.test/do-report
         {:type     :fail
          :message  ~msg
          :expected '~form
          :actual   (str "The second argument of match? isn't a matcher")}))))

(defmethod clojure.test/report :mismatch [m]
  (clojure.test/with-test-out
    (clojure.test/inc-report-counter :fail)
    (println "\nFAIL in" (clojure.test/testing-vars-str m))
    (when (seq clojure.test/*testing-contexts*)
      (println (clojure.test/testing-contexts-str)))
    (when-let [message (:message m)]
      (println message))
    (println "mismatch:")
    (printer/print (:markup m))))
