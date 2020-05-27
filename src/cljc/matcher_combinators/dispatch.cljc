(ns matcher-combinators.dispatch
  "Type-specific implementations of the `match` function of the
  matcher-combinators.core/Match protocol invoke dispatch functions defined in
  this namespace, which provide a layer of indirection between `match`
  and the specific matcher implementation for each type. This indirection allows
  for redefinition at runtime, necessary for the `match-with` feature."
  (:require [matcher-combinators.matchers :as matchers]
            [matcher-combinators.core :as core])
  #?(:clj
     (:import [clojure.lang Keyword Symbol Ratio BigInt IPersistentMap
               IPersistentVector IPersistentList IPersistentSet
               LazySeq Repeat Cons Var]
              [java.util UUID Date]
              [java.util.regex Pattern]
              [java.time LocalDate LocalDateTime LocalTime YearMonth])))

(defn- cljs-uri [expected]
  (core/->CljsUriEquals expected))

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
     :cljs (cljs-uri expected)))
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
(defn function-dispatch [expected] (partial core/match-pred expected (str "predicate: " expected)))

(def type->dispatch
  #?(:cljs {}
     :clj {nil                            #'nil-dispatch
           java.lang.Class                #'class-dispatch
           Object                         #'object-dispatch
           java.lang.Integer              #'integer-dispatch
           java.lang.Short                #'short-dispatch
           java.lang.Long                 #'long-dispatch
           java.lang.Float                #'float-dispatch
           java.lang.Double               #'double-dispatch
           java.lang.String               #'string-dispatch
           clojure.lang.Symbol            #'symbol-dispatch
           clojure.lang.Keyword           #'keyword-dispatch
           java.lang.Boolean              #'boolean-dispatch
           java.util.UUID                 #'uuid-dispatch
           java.util.Date                 #'date-dispatch
           java.time.LocalDate            #'local-date-dispatch
           java.time.LocalDateTime        #'local-date-time-dispatch
           java.time.LocalTime            #'local-time-dispatch
           java.time.YearMonth            #'year-month-dispatch
           clojure.lang.Ratio             #'ratio-dispatch
           java.math.BigDecimal           #'big-decimal-dispatch
           java.math.BigInteger           #'big-integer-dispatch
           clojure.lang.BigInt            #'big-int-dispatch
           java.lang.Character            #'character-dispatch
           clojure.lang.Var               #'var-dispatch

           clojure.lang.IPersistentMap              #'i-persistent-map-dispatch
           clojure.lang.IPersistentVector           #'i-persistent-vector-dispatch
           clojure.lang.PersistentVector$ChunkedSeq #'chunked-seq-dispatch
           clojure.lang.IPersistentList             #'i-persistent-list-dispatch
           clojure.lang.IPersistentSet              #'i-persistent-list-dispatch
           clojure.lang.Cons                        #'cons-dispatch
           clojure.lang.Repeat                      #'repeat-dispatch
           clojure.lang.LazySeq                     #'lazy-seq-dispatch
           clojure.lang.ArraySeq                    #'array-seq-dispatch
           java.util.regex.Pattern                  #'pattern-dispatch}))

(def type-symbol->dispatch
  (->> type->dispatch
       (map (fn [[k v]] [(-> k pr-str symbol) v]))
       (into {})))

(defn var->qualified-ref [datatype]
  (symbol (str
            (-> type-symbol->dispatch datatype meta :ns ns-name)
            "/"
            (-> type-symbol->dispatch datatype meta :name))))

(defn match-with-inner [type->default-matcher body]
  (when-not (map? type->default-matcher)
    (throw (ex-info "Override argument to `wrap-match-with` must be a base map value"
                    {:expected-type 'map
                     :provided-type (type type->default-matcher)})))
  (let [dispatch-vars+matcher-targets (mapcat
                                        (fn [[k v]] [(var->qualified-ref k) v])
                                        type->default-matcher)]
    `(with-redefs [~@dispatch-vars+matcher-targets]
      ~body)))

(defmacro wrap-match-with [type->default-matcher body]
  (match-with-inner type->default-matcher body))
