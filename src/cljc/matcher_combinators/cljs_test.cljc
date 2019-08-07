(ns matcher-combinators.cljs-test
  #?(:cljs
     (:require-macros [matcher-combinators.cljs-test]))
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [cljs.test :as t :refer-macros [deftest is]]))

(defn with-file+line-info [report]
  #?(:cljs (merge (t/file-and-line (js/Error.) 4) report)))

;; This technique was copied from https://github.com/tonsky/datascript
;; below is the reasoning from the datascript repo:

;;  The matcher-combinators.cljs-test namespace exists only for the side
;;  effect of extending the cljs.test/assert-expr multimethod.

;;  This has to be done on the clj side of cljs compilation, and
;;  so we have a separate namespace that is only loaded by cljs
;;  via a :require-macros clause in datascript.test.core. This
;;  means we have a clj namespace that should only be loaded by
;;  cljs compilation.

#?(:clj (do
(defmethod t/assert-expr 'match? [_ msg form]
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
         (t/do-report
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
       (t/do-report
         {:type     :fail
          :message  ~msg
          :expected (str "The first argument of match? needs to be a matcher (implement the match protocol)")
          :actual   '~form}))))

(defmethod t/assert-expr 'match-with? [_ msg form]
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

(defmethod t/assert-expr 'thrown-match? [_ msg form]
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
              (t/do-report {:type     :fail
                            :message  ~msg
                            :expected '~form
                            :actual   (symbol "the expected exception wasn't thrown")})))
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
  (when (seq (:testing-contexts (t/get-current-env)))
    (println (t/testing-contexts-str)))
  (when-let [message (:message m)]
    (println message))
  (println (:ex-class m) "data mismatches:")
  (printer/pretty-print (:markup m)))

(defmethod t/report [::t/default :matcher-combinators/mismatch] [m]
  (t/inc-report-counter! :fail)
  (println "\nFAIL in" (t/testing-vars-str m))
  (when (seq (:testing-contexts (t/get-current-env)))
    (println (t/testing-contexts-str)))
  (when-let [message (:message m)]
    (println message))
  (println "mismatch:")
  (printer/pretty-print (:markup m)))))
