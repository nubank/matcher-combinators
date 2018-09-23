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
  (:import [midje.data.metaconstant Metaconstant]))

(defn check-match [matcher actual]
  (if (exception/captured-throwable? actual)
    (checking/as-data-laden-falsehood
      {:notes [(exception/friendly-stacktrace actual)]})
    (let [result (core/match matcher actual)]
      (if (core/match? result)
        true
        (checking/as-data-laden-falsehood {:notes [(printer/as-string result)]})))))

(checkers.defining/defchecker match [matcher]
  (checkers.defining/checker [actual]
    (if (core/matcher? matcher)
      (check-match matcher actual)
      (checking/as-data-laden-falsehood
        {:notes [(str "Input wasn't a matcher: " matcher)]}))))

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

