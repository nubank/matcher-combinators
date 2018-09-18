(ns matcher-combinators.test
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.parser]
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

(defmethod clojure.core/print-method ::mismatch [result out]
  (binding [*out* out]
    (println)
    (printer/pretty-print result)))

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
              {:type     :fail
               :message  ~msg
               :expected (list 'match? '...)
               :actual   (vary-meta (second result#) assoc :type ::mismatch)}))))
       (clojure.test/do-report
        {:type     :fail
         :message  ~msg
         :expected '~form
         :actual   (str "The second argument of match? isn't a matcher")}))))
