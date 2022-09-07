(ns ^:deprecated matcher-combinators.midje
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model]
            [matcher-combinators.parser]
            [matcher-combinators.printer :as printer]
            [matcher-combinators.result :as result]
            [matcher-combinators.utils :as utils]
            [midje.checking.checkers.defining :as checkers.defining]
            [midje.checking.core :as checking]
            [midje.data.metaconstant] ; otherwise Metaconstant class cannot be found
            [midje.util.exceptions :as exception]
            [midje.util.thread-safe-var-nesting :as thread-safe-var-nesting])
  (:import [clojure.lang ArityException]
           [midje.data.metaconstant Metaconstant]
           [midje.util.exceptions ICapturedThrowable]))

(defn ^:deprecated check-match
  "DEPRECATED: support for midje in matcher-combinators is deprecated."
  [matcher actual]
  (if (exception/captured-throwable? actual)
    (checking/as-data-laden-falsehood
     {:notes [(exception/friendly-stacktrace actual)]})
    (let [{::result/keys [type value] :as result} (core/match matcher actual)]
      (if (core/indicates-match? result)
        true
        (checking/as-data-laden-falsehood {:notes [(printer/as-string [type value])]})))))

(checkers.defining/defchecker match
  "DEPRECATED: support for midje in matcher-combinators is deprecated.
  
  Takes in a matcher and returns a checker that asserts that the provided value satisfies the matcher"
  [matcher]
  (checkers.defining/checker [actual]
                             (if (core/matcher? matcher)
                               (check-match matcher actual)
                               (checking/as-data-laden-falsehood
                                {:notes [(str "Input wasn't a matcher: " matcher)]}))))

(defmacro ^{:deprecated true
            :doc "DEPRECATED: support for midje in matcher-combinators is deprecated.
            
                 Validates that the provided values satisfies the matcher but
                 uses the provided type->matcher map to redefine the default
                 matchers used for the specified types.

                 By default when the system sees a number, it applies
                 the `equals` matcher to it.  So if, for example, we want to
                 match ints by their absolute value, we could do this:

                     (defn abs-value-matcher [expected]
                       (core/->PredMatcher
                        (fn [actual] (= (Math/abs expected)
                                        (Math/abs actual)))
                        (str \"equal to abs value of \" expected)))

                     (match-with [int? abs-value-matcher])

                 NOTE: currently doesn't work in midje `provided` expressions"
            :arglists '([type->default-matcher]
                        [type->default-matcher matcher])}
  match-with
  [& args]
  (let [arg-count                         (count args)
        [type->default-matcher expected] args]
    (case arg-count
      1 `(fn [expected#]
           (fn [actual#]
             (if (core/matcher? expected#)
               (check-match
                (matchers/match-with ~type->default-matcher expected#)
                actual#)
               (checking/as-data-laden-falsehood
                {:notes [(str "Input wasn't a matcher: " expected#)]}))))
      2 `(fn [actual#]
           (if (core/matcher? ~expected)
             (check-match
              (matchers/match-with ~type->default-matcher ~expected)
              actual#)
             (checking/as-data-laden-falsehood
              {:notes [(str "Input wasn't a matcher: " ~expected)]})))
      (throw (ArityException. arg-count "expected 1 or 2 arguments")))))

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
  "DEPRECATED: support for midje in matcher-combinators is deprecated.
  
  Takes in a matcher or a matcher and throwable subclass.
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
  (-match [this actual]
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

(def match-equals
  "DEPRECATED: support for midje in matcher-combinators is deprecated.
  
  match but using strict `equals` matching behavior for maps, even nested ones."
  (match-with [map? matchers/equals]))

(def match-roughly
  "DEPRECATED: support for midje in matcher-combinators is deprecated.
  
  match where all numbers match if they are within the delta of their expected value"
  (fn [delta matcher]
    (let [func (fn [expected] (core/->PredMatcher (fn [actual]
                                                    (utils/within-delta? delta expected actual))
                                                  (str "roughly " expected " (+/- " delta ")")))]
      (match-with
       {java.lang.Integer    func
        java.lang.Short      func
        java.lang.Long       func
        java.lang.Float      func
        java.lang.Double     func
        java.math.BigDecimal func
        java.math.BigInteger func
        clojure.lang.BigInt  func}
       matcher))))
