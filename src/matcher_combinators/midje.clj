(ns matcher-combinators.midje
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.model :as model]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [midje.data.metaconstant] ; otherwise Metaconstant class cannot be found
            [matcher-combinators.printer :as printer]
            [midje.checking.core :as checking]
            [midje.util.exceptions :as exception]
            [midje.util.thread-safe-var-nesting :as thread-safe-var-nesting]
            [midje.checking.checkers.defining :as checkers.defining])
  (:import [clojure.lang ArityException]
           [midje.data.metaconstant Metaconstant]
           [midje.util.exceptions ICapturedThrowable]))

(defn check-match [matcher actual]
  (if (exception/captured-throwable? actual)
    (checking/as-data-laden-falsehood
     {:notes [(exception/friendly-stacktrace actual)]})
    (let [{::result/keys [type value] :as result} (core/match matcher actual)]
      (if (core/match? result)
        true
        (checking/as-data-laden-falsehood {:notes [(printer/as-string [type value])]})))))

(checkers.defining/defchecker match
                              "Takes in a matcher and returns a checker that asserts that the provided value satisfies the matcher"
                              [matcher]
  (checkers.defining/checker [actual]
                             (if (core/matcher? matcher)
                               (check-match matcher actual)
                               (checking/as-data-laden-falsehood
                                {:notes [(str "Input wasn't a matcher: " matcher)]}))))


(defn- parse-throws-args! [args]
  (let [arg-count (count args)]
    (case arg-count
      1 (if (core/matcher? (first args))
          [(first args) nil]
          (throw (IllegalArgumentException.
                   "1-arity throws-match must be provided a matcher")))
      2 (if (and (core/matcher? (first args))
                 (isa? (second args) Throwable))
          [(first args) (second args)]
          (throw (IllegalArgumentException.
                   "2-arity throws-match must be provided a matcher and a throwable subclass")))
      (throw (ArityException. arg-count "throws-match")))))

(checkers.defining/defchecker throws-match
                              "Takes in a matcher or a matcher and throwable subclass.
                              Returns a checker that asserts an exception was raised and the ex-data within it satisfies the matcher"
                              [& args]
  (checkers.defining/checker [actual]
     (if-not (instance? ICapturedThrowable actual)
       false
       (let [[matcher ex-class] (parse-throws-args! args)
             throwable          (.throwable ^ICapturedThrowable actual)]
         (cond
           (and ex-class
                (not (instance? ex-class throwable)))
           (checking/as-data-laden-falsehood
             {:notes [(str "Unexpected exception (" throwable ") was raised instead of "
                           ex-class)]})

           (core/matcher? matcher)
           (check-match matcher (ex-data throwable))

           :else
           (checking/as-data-laden-falsehood
             {:notes [(str "Input wasn't a matcher: " matcher)]}))))))

(extend-protocol core/Matcher
  Metaconstant
  (match [this actual]
    (if (and (or (symbol? actual)
                 (= (type actual) Metaconstant)
                 (= actual thread-safe-var-nesting/unbound-marker))
             (.equals this actual))
      {::result/type   :match
       ::result/value  actual
       ::result/weight 0}
      (let [mismatch-val (if (and (keyword? actual)
                                  (= ::core/missing actual))
                           (model/->Missing this)
                           (model/->Mismatch this actual))]
        {::result/type   :mismatch
         ::result/value  mismatch-val
         ::result/weight 1}))))

