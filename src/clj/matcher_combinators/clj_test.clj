(ns matcher-combinators.clj-test
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.dispatch :as dispatch]
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
    (or (str/starts-with? cl-name "matcher_combinators.clj-test$")
        (str/starts-with? cl-name "java.lang."))))

;; had to include this from `clojure.test` because there is no good way to run
;; this logic when not reporting a `:fail` or `:error`.
(defn with-file+line-info [report]
  (->> (.getStackTrace (Thread/currentThread))
       (drop-while core-or-this-class-name?)
       stacktrace-file-and-line
       (merge report)))

(defn tagged-for-pretty-printing [actual-summary result]
  (with-meta {:summary      actual-summary
              :match-result result}
    {:type ::mismatch}))

(defmethod clojure.test/assert-expr 'match? [msg form]
  `(let [args#              (list ~@(rest form))
         [matcher# actual#] args#]
     (cond
       (not (= 2 (count args#)))
       (clojure.test/do-report
        {:type     :fail
         :message  ~msg
         :expected (symbol "`match?` expects 2 arguments: a `matcher` and the `actual`")
         :actual   (symbol (str (count args#) " were provided: " '~form))})

       (core/matcher? matcher#)
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
               :expected '~form
               :actual   (tagged-for-pretty-printing (list '~'not (list 'match? matcher# actual#))
                                                     result#)}))))

       :else
       (clojure.test/do-report
        {:type     :fail
         :message  ~msg
         :expected (str "The first argument of match? needs to be a matcher (implement the match protocol)")
         :actual   '~form}))))

(defmethod clojure.test/assert-expr 'match-with? [msg form]
  (let [args           (rest form)
        [type->matcher
         matcher
         actual]       args]
    (dispatch/match-with-inner
     type->matcher
     `(cond
        (not (= 3 (count '~args)))
        (clojure.test/do-report
         {:type     :fail
          :message  ~msg
          :expected (symbol "`match-with?` expects 3 arguments: a `type->matcher` map, a `matcher`, and the `actual`")
          :actual   (symbol (str (count '~args) " were provided: " '~form))})

        (core/matcher? ~matcher)
        (let [result# (core/match ~matcher ~actual)]
          (clojure.test/do-report
           (if (core/match? result#)
             {:type     :pass
              :message  ~msg
              :expected '~form
              :actual   (list 'match? ~matcher ~actual)}
             (with-file+line-info
               {:type     :fail
                :message  ~msg
                :expected '~form
                :actual   (tagged-for-pretty-printing (list '~'not (list 'match? ~matcher ~actual))
                                                      result#)}))))

        :else
        (clojure.test/do-report
         {:type     :fail
          :message  ~msg
          :expected (str "The first argument of match? needs to be a matcher (implement the match protocol)")
          :actual   '~form})))))

(defmethod clojure.test/assert-expr 'thrown-match? [msg form]
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
              (clojure.test/do-report {:type     :fail
                                       :message  ~msg
                                       :expected '~form
                                       :actual   (symbol "the expected exception wasn't thrown")})))
          (catch ~klass e#
            (let [result# (core/match ~matcher (ex-data e#))]
              (clojure.test/do-report
               (if (core/match? result#)
                 {:type     :pass
                  :message  ~msg
                  :expected '~form
                  :actual   (list 'thrown-match? ~klass ~matcher '~body)}
                 (with-file+line-info
                   {:type     :fail
                    :message  ~msg
                    :expected '~form
                    :actual   (tagged-for-pretty-printing (list '~'not (list 'thrown-match? ~klass ~matcher '~body))
                                                          result#)
                    :ex-class ~klass}))))
            e#))))

(defmethod clojure.core/print-method ::mismatch [{:keys [match-result]} out]
  (binding [*out* out]
    (printer/pretty-print (::result/value match-result))))
