(ns matcher-combinators.clj-test
  "Internal use. Require `matcher-combinators.test` instead of this
  namespace."
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [matcher-combinators.utils :as utils]
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
    (or (str/starts-with? cl-name "matcher_combinators.clj_test$")
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
       (let [result# (core/match matcher# actual#)
             match?# (core/indicates-match? result#)]
         (clojure.test/do-report
          (if match?#
            {:type     :pass
             :message  ~msg
             :expected '~form
             :actual   (list 'match? matcher# actual#)}
            (with-file+line-info
              {:type     :fail
               :message  ~msg
               :expected '~form
               :actual   (tagged-for-pretty-printing (list '~'not (list 'match? matcher# actual#))
                                                     result#)})))
         match?#)

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
    `(cond
       (not (= 3 (count '~args)))
       (clojure.test/do-report
        {:type     :fail
         :message  ~msg
         :expected (symbol "`match-with?` expects 3 arguments: a `type->matcher` map, a `matcher`, and the `actual`")
         :actual   (symbol (str (count '~args) " were provided: " '~form))})

       (core/matcher? ~matcher)
       (let [result# (core/match
                      (matchers/match-with ~type->matcher ~matcher)
                      ~actual)]
         (clojure.test/do-report
          (if (core/indicates-match? result#)
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
         :expected (str "The second argument of match-with? needs to be a matcher (implement the match protocol)")
         :actual   '~form}))))

(defmethod clojure.test/assert-expr 'thrown-match? [msg form]
  ;; 2-arity: (is (thrown-with-match? matcher expr))
  ;; 3-arity: (is (thrown-with-match? exception-class matcher expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the exception data satisfies the provided matcher.
  (let [arity   (count (rest form))
        klass   (if (<= arity 2) 'clojure.lang.ExceptionInfo (nth form 1))
        matcher (nth form (dec arity))
        body    (nthnext form (dec arity))]
    `(if (not (<= 2 ~arity 3))
       (clojure.test/do-report
        {:type     :fail
         :message  ~msg
         :expected (symbol "`thrown-match?` expects 2 or 3 arguments: an optional exception class, a `matcher`, and the `actual`")
         :actual   (symbol (str ~arity " argument(s) provided: " '~form))})
       (try ~@body
            (if (isa? ~matcher Throwable)
              (clojure.test/do-report
               {:type     :fail
                :message  ~msg
                :expected (symbol "an exception class has been provided, but one of the `matcher` or `actual` arguments is missing")
                :actual   (symbol (str ~arity " argument(s) provided: " '~form))})
              (clojure.test/do-report {:type     :fail
                                       :message  ~msg
                                       :expected '~form
                                       :actual   (symbol "the expected exception wasn't thrown")}))
            (catch ~klass e#
              (let [result# (core/match ~matcher (ex-data e#))]
                (clojure.test/do-report
                 (if (core/indicates-match? result#)
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
              e#)))))

(defmethod clojure.core/print-method ::mismatch [{:keys [match-result]} out]
  (binding [*out* out]
    (printer/pretty-print (::result/value match-result))))

(defn build-match-assert
  "Allows you to define a custom clojure.test match assert:


  `(defmethod clojure.test/assert-expr 'baz? [msg form]
    (build-match-assert 'baz? {java.lang.Long greater-than-matcher} msg form))`"
  [match-assert-name type->matcher msg form]
  (let [args             (rest form)
        [matcher actual] args]
    `(let [matcher#       ~matcher
           actual#        ~actual
           type->matcher# ~type->matcher]
       (cond
         (not (= 2 (count '~args)))
         (clojure.test/do-report
          {:type     :fail
           :message  ~msg
           :expected (symbol (str "`" '~match-assert-name "` expects 3 arguments: a `type->matcher` map, a `matcher`, and the `actual`"))
           :actual   (symbol (str (count '~args) " were provided: " '~form))})

         (core/matcher? matcher#)
         (let [result# (core/match
                        (matchers/match-with
                         type->matcher#
                         matcher#)
                        actual#)]
           (clojure.test/do-report
            (if (core/indicates-match? result#)
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
           :expected (str "The second argument of " '~match-assert-name " needs to be a matcher (implement the match protocol)")
           :actual   '~form})))))

(defmethod clojure.test/assert-expr 'match-equals? [msg form]
  (build-match-assert 'match-equals?
                      {clojure.lang.IPersistentMap matchers/equals}
                      msg
                      form))

(defmethod clojure.test/assert-expr 'match-roughly? [msg form]
  (let [directive (first form)
        delta     (second form)
        the-rest  (rest (rest form))
        roughly-delta?  `(fn [expected#]
                           (core/->PredMatcher (fn [actual#]
                                                 (utils/roughly? expected# actual# ~delta))
                                               (str "roughly " expected# " (+/- " ~delta ")")))
        form' (concat [directive] the-rest)]
    `(if (not (= 3 (count '~(rest form))))
       (clojure.test/do-report
         {:type     :fail
          :message  ~msg
          :expected (symbol (str "`" '~directive "` expects 3 arguments: a `delta` number, a `matcher`, and the `actual`"))
          :actual   (symbol (str (count '~(rest form)) " were provided: " '~form))})
       ~(build-match-assert 'match-roughly?
                            {java.lang.Integer    roughly-delta?
                             java.lang.Short      roughly-delta?
                             java.lang.Long       roughly-delta?
                             java.lang.Float      roughly-delta?
                             java.lang.Double     roughly-delta?
                             java.math.BigDecimal roughly-delta?
                             java.math.BigInteger roughly-delta?
                             clojure.lang.BigInt  roughly-delta?}
                            msg
                            form'))))
