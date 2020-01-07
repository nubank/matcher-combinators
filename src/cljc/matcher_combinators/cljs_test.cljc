(ns matcher-combinators.cljs-test
  #?(:cljs
     (:require-macros [matcher-combinators.cljs-test]))
  (:require [clojure.string :as str]
            [matcher-combinators.core :as core]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]))

(when (find-ns 'cljs.test)
  (require '[cljs.test :as t :refer-macros [deftest is]]))

  ;;  The matcher-combinators.cljs-test namespace exists only for the side
  ;;  effect of extending the cljs.test/assert-expr multimethod.

  ;;  This has to be done on the clj side of cljs compilation, and
  ;;  so we have a separate namespace that is only loaded by cljs
  ;;  via a :require-macros clause in datascript.test.core. This
  ;;  means we have a clj namespace that should only be loaded by
  ;;  cljs compilation.

(defmacro maybedefmethod [multifn & rest]
  #?(:clj  (let [ns (symbol (namespace multifn))]
             (try
               (require ns)
               `(clojure.core/defmethod ~multifn ~@rest)
               (catch Exception e)))
     :cljs `(defmethod ~multifn ~@rest)))

(defn- stacktrace-file-and-line
  [stacktrace]
  (if (seq stacktrace)
    (let [^StackTraceElement s (first stacktrace)]
      {:file (.getFileName s) :line (.getLineNumber s)})
    {:file nil :line nil}))

(defn- core-or-this-class-name? [^StackTraceElement stacktrace]
  (let [cl-name (.getClassName stacktrace)]
    (or (str/starts-with? cl-name "matcher_combinators.clj_test$")
        (str/starts-with? cl-name "java.lang."))))

(defn with-file+line-info [report]
  #?(:clj (->> (.getStackTrace (Thread/currentThread))
               (drop-while core-or-this-class-name?)
               stacktrace-file-and-line
               (merge report))))

#?(:clj (do
(maybedefmethod cljs.test/assert-expr 'match? [_ msg form]
  `(let [args#              (list ~@(rest form))
         [matcher# actual#] args#]
     (cond
       (not (= 2 (count args#)))
       (cljs.test/do-report
        {:type     :fail
         :message  ~msg
         :expected (symbol "`match?` expects 2 arguments: a `matcher` and the `actual`")
         :actual   (symbol (str (count args#) " were provided: " '~form))})

       (core/matcher? matcher#)
       (let [result# (core/match matcher# actual#)]
         (cljs.test/do-report
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
       :else
       (cljs.test/do-report
         {:type     :fail
          :message  ~msg
          :expected (str "The first argument of match? needs to be a matcher (implement the match protocol)")
          :actual   '~form}))))

(maybedefmethod cljs.test/assert-expr 'match-with? [_ msg form]
  `(clojure.test/do-report
     {:type     :fail
      :message  ~msg
      :expected (symbol "`match-with?` not yet implemented for cljs")
      :actual   '~form}))

(maybedefmethod cljs.test/assert-expr 'thrown-match? [_ msg form]
  ;; (is (thrown-with-match? exception-class matcher expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the exception data satisfies the provided matcher.
  (let [klass   (nth form 1)
        matcher (nth form 2)
        body    (nthnext form 3)]
    `(try ~@body
          (let [args# (list ~@(rest form))]
            (if (not (= 3 (count args#)))
              (clojure.test/do-report
               {:type     :fail
                :message  ~msg
                :expected (symbol "`thrown-match?` expects 3 arguments: an exception class, a `matcher`, and the `actual`")
                :actual   (symbol (str (count args#) " were provided: " '~form))})
              (cljs.test/do-report {:type     :fail
                            :message  ~msg
                            :expected '~form
                            :actual   (symbol "the expected exception wasn't thrown")})))
          (catch ~klass e#
            (let [result# (core/match ~matcher (ex-data e#))]
              (cljs.test/do-report
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
(defmethod cljs.test/report [:cljs.test/default :matcher-combinators/exception-mismatch] [m]
  (cljs.test/inc-report-counter! :fail)
  (println "\nFAIL in" (cljs.test/testing-vars-str m))
  (when (seq (:testing-contexts (cljs.test/get-current-env)))
    (println (cljs.test/testing-contexts-str)))
  (when-let [message (:message m)]
    (println message))
  (println (:ex-class m) "data mismatches:")
  (printer/pretty-print (:markup m)))

(defmethod cljs.test/report [:cljs.test/default :matcher-combinators/mismatch] [m]
  (cljs.test/inc-report-counter! :fail)
  (println "\nFAIL in" (cljs.test/testing-vars-str m))
  (when (seq (:testing-contexts (cljs.test/get-current-env)))
    (println (cljs.test/testing-contexts-str)))
  (when-let [message (:message m)]
    (println message))
  (println "mismatch:")
  (printer/pretty-print (:markup m)))))
