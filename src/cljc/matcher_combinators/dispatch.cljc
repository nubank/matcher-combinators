(ns matcher-combinators.dispatch
  "Type-specific implementations of the `match` function of the
  matcher-combinators.core/Match protocol invoke dispatch functions defined in
  this namespace, which provide a layer of indirection between `match`
  and the specific matcher implementation for each type. This indirection allows
  for redefinition at runtime, necessary for the `match-with` feature."
  (:require [matcher-combinators.matchers :as matchers])
  #?(:clj
     (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap
               IPersistentVector IPersistentList IPersistentSet
               LazySeq Repeat Cons Var]
              [java.util UUID Date]
              [java.util.regex Pattern]
              [java.time LocalDate LocalDateTime LocalTime YearMonth])))

;; equals base types
(defn nil-dispatch [expected] (matchers/equals expected))
(defn class-dispatch [expected] (matchers/equals expected))
(defn object-dispatch [expected] (matchers/equals expected))
(defn integer-dispatch [expected] (matchers/equals expected))
(defn short-dispatch [expected] (matchers/equals expected))
(defn long-dispatch [expected] (matchers/equals expected))
(defn float-dispatch [expected] (matchers/equals expected))
(defn double-dispatch [expected] (matchers/equals expected))
(defn string-dispatch [expected] (matchers/equals expected))
(defn symbol-dispatch [expected] (matchers/equals expected))
(defn keyword-dispatch [expected] (matchers/equals expected))
(defn boolean-dispatch [expected] (matchers/equals expected))
(defn uuid-dispatch [expected] (matchers/equals expected))
(defn uri-dispatch [expected]
  #?(:clj  (matchers/equals expected)
     :cljs (matchers/cljs-uri expected)))
(defn date-dispatch [expected] (matchers/equals expected))
(defn local-date-dispatch [expected] (matchers/equals expected))
(defn local-date-time-dispatch [expected] (matchers/equals expected))
(defn local-time-dispatch [expected] (matchers/equals expected))
(defn year-month-dispatch [expected] (matchers/equals expected))
(defn ratio-dispatch [expected] (matchers/equals expected))
(defn big-decimal-dispatch [expected] (matchers/equals expected))
(defn big-integer-dispatch [expected] (matchers/equals expected))
(defn big-int-dispatch [expected] (matchers/equals expected))
(defn character-dispatch [expected] (matchers/equals expected))
(defn var-dispatch [expected] (matchers/equals expected))

;; equals compound types
(defn i-persistent-vector-dispatch [expected] (matchers/equals expected))
(defn chunked-seq-dispatch [expected] (matchers/equals expected))
(defn i-persistent-list-dispatch [expected] (matchers/equals expected))
(defn i-persistent-set-dispatch [expected] (matchers/equals expected))
(defn cons-dispatch [expected] (matchers/equals expected))
(defn repeat-dispatch [expected] (matchers/equals expected))
(defn lazy-seq-dispatch [expected] (matchers/equals expected))
(defn array-seq-dispatch [expected] (matchers/equals expected))

;; embeds compound types
(defn i-persistent-map-dispatch [expected] (matchers/embeds expected))

;; other
(defn pattern-dispatch [expected] (matchers/regex expected))
(defn function-dispatch [f] (matchers/pred f))

(def type->dispatch
  #?(:cljs {}
     :clj {'nil                                      'matcher-combinators.dispatch/nil-dispatch
           'java.lang.Class                          'matcher-combinators.dispatch/class-dispatch
           'Object                                   'matcher-combinators.dispatch/object-dispatch
           'java.lang.Integer                        'matcher-combinators.dispatch/integer-dispatch
           'java.lang.Short                          'matcher-combinators.dispatch/short-dispatch
           'java.lang.Long                           'matcher-combinators.dispatch/long-dispatch
           'java.lang.Float                          'matcher-combinators.dispatch/float-dispatch
           'java.lang.Double                         'matcher-combinators.dispatch/double-dispatch
           'java.lang.String                         'matcher-combinators.dispatch/string-dispatch
           'clojure.lang.Symbol                      'matcher-combinators.dispatch/symbol-dispatch
           'clojure.lang.Keyword                     'matcher-combinators.dispatch/keyword-dispatch
           'java.lang.Boolean                        'matcher-combinators.dispatch/boolean-dispatch
           'java.util.UUID                           'matcher-combinators.dispatch/uuid-dispatch
           'java.util.Date                           'matcher-combinators.dispatch/date-dispatch
           'java.time.LocalDate                      'matcher-combinators.dispatch/local-date-dispatch
           'java.time.LocalDateTime                  'matcher-combinators.dispatch/local-date-time-dispatch
           'java.time.LocalTime                      'matcher-combinators.dispatch/local-time-dispatch
           'java.time.YearMonth                      'matcher-combinators.dispatch/year-month-dispatch
           'clojure.lang.Ratio                       'matcher-combinators.dispatch/ratio-dispatch
           'java.math.BigDecimal                     'matcher-combinators.dispatch/big-decimal-dispatch
           'java.math.BigInteger                     'matcher-combinators.dispatch/big-integer-dispatch
           'clojure.lang.BigInt                      'matcher-combinators.dispatch/big-int-dispatch
           'java.lang.Character                      'matcher-combinators.dispatch/character-dispatch
           'clojure.lang.Var                         'matcher-combinators.dispatch/var-dispatch

           'clojure.lang.IPersistentMap              'matcher-combinators.dispatch/i-persistent-map-dispatch
           'clojure.lang.IPersistentVector           'matcher-combinators.dispatch/i-persistent-vector-dispatch
           'clojure.lang.PersistentVector$ChunkedSeq 'matcher-combinators.dispatch/chunked-seq-dispatch
           'clojure.lang.IPersistentList             'matcher-combinators.dispatch/i-persistent-list-dispatch
           'clojure.lang.IPersistentSet              'matcher-combinators.dispatch/i-persistent-list-dispatch
           'clojure.lang.Cons                        'matcher-combinators.dispatch/cons-dispatch
           'clojure.lang.Repeat                      'matcher-combinators.dispatch/repeat-dispatch
           'clojure.lang.LazySeq                     'matcher-combinators.dispatch/lazy-seq-dispatch
           'clojure.lang.ArraySeq                    'matcher-combinators.dispatch/array-seq-dispatch
           'java.util.regex.Pattern                  'matcher-combinators.dispatch/pattern-dispatch}))

(defn match-with-inner [type->default-matcher body]
  (when-not (map? type->default-matcher)
    (throw (ex-info "First argument to `match-with` must be a map"
                    {:expected-type 'map
                     :provided-type (type type->default-matcher)})))
  (let [dispatch-vars+matcher-targets (mapcat (fn [[k v]] [(type->dispatch k) v])
                                              type->default-matcher)]
    `(with-redefs [~@dispatch-vars+matcher-targets]
      ~body)))

(defmacro wrap-match-with [type->default-matcher body]
  (match-with-inner type->default-matcher body))
