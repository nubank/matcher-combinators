(ns matcher-combinators.midje
  (:require [matcher-combinators.core :as core]
            [matcher-combinators.model :as model]
            [matcher-combinators.parser :as parser]
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

(checkers.defining/defchecker equals-match [matcher]
  (checkers.defining/checker [actual]
    (with-redefs [parser/map-dispatch (fn [exp] (core/->EqualsMap exp))]
      (if (core/matcher? matcher)
        (check-match matcher actual)
        (checking/as-data-laden-falsehood
          {:notes [(str "Input wasn't a matcher: " matcher)]})))))

(extend-protocol core/Matcher
  Metaconstant
  (match [this actual]
    (if (and (or (symbol? actual)
                 (= (type actual) Metaconstant)
                 (= actual thread-safe-var-nesting/unbound-marker))
             (.equals this actual))
      [:match actual]
      (if (and (keyword? actual)
               (= ::core/missing actual))
        [:mismatch (model/->Missing this)]
        [:mismatch (model/->Mismatch this actual)]))))
