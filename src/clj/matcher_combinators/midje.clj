(ns matcher-combinators.midje
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.model :as model]
            [matcher-combinators.parser]
            [matcher-combinators.result :as result]
            [matcher-combinators.utils :as utils]
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

(defmacro ^{:doc "Validates that the provided values satisfies the matcher but
                 uses the provided type->matcher map to redefine the default
                 matchers used for the specified types.

                 By default when the system sees a `java.lang.Long` it applies
                 the `equals` matcher to it.  So if, for example, we want to
                 check that all Longs are greater than whatever Long provided
                 in the matcher, we would do:

                 `(defn greater-than-matcher [expected-long]
                    (matcher-combinators.core/->PredMatcher
                      (fn [actual] (> actual expected-long))
                      (str \"greater than \" expected-long)))

                 (match-with {java.lang.Long greater-than-matcher})`

                 NOTE: currently doesn't work in midje `provided` expressions"
            :arglists '([type->default-matcher]
                        [type->default-matcher matcher])}
  match-with
  [& args]
  (let [arg-count  (count args)
        ;; Not exactly hygenic or whatever, but that'll do pig, that'll do.
        ;; Needed because I don't know how to deal with auto-gensym-ing in the
        ;; context of nested quoting
        actual-var (gensym 'actual)]
    (case arg-count
      1 (let [[type->default-matcher] args
              matcher-var             (gensym 'mathcer)]
          `(fn [~matcher-var]
             (fn [~actual-var]
               ~(dispatch/match-with-inner
                 type->default-matcher
                 `(if (core/matcher? ~matcher-var)
                    (check-match ~matcher-var ~actual-var)
                    (checking/as-data-laden-falsehood
                     {:notes [(str "Input wasn't a matcher: " ~matcher-var)]}))))))
      2 (let [[type->default-matcher matcher] args]
          `(fn [~actual-var]
             ~(dispatch/match-with-inner
               type->default-matcher
               `(if (core/matcher? ~matcher)
                  (check-match ~matcher ~actual-var)
                  (checking/as-data-laden-falsehood
                   {:notes [(str "Input wasn't a matcher: " ~matcher)]})))))
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
  "match but using strict `equals` matching behavior for maps, even nested ones."
  (match-with {clojure.lang.IPersistentMap matchers/equals}))

(def match-roughly
  "match where all numbers match if they are within the delta of their expected value"
  (fn [delta matcher]
    (let [func (fn [expected] (core/->PredMatcher (fn [actual]
                                                    (utils/roughly? expected actual delta))
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
