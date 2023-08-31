(ns matcher-combinators.config
  "Global output behavior configurations"
  (:require [matcher-combinators.ansi-color :as ansi-color]))

;; Redacting fully-matched data-structures from output
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:dynamic true
       :doc "thread-local way to control, via `binding`, the redacting of fully-matched data-structures in the matcher-combinator output"}
  *use-redaction*
  false)

(defn- set-use-redaction!
  "internal function, use matcher-combinators.config/enable-redaction!"
  [v]
  #?(:clj (alter-var-root #'*use-redaction* (constantly v))
     :cljs (set! *use-redaction* v)))

(defn enable-redaction!
  "Thread-global way to enable the redaction of fully-matched data-structures in matcher-combinator output."
  []
  (set-use-redaction! true))

(defn disable-redaction!
  "Thread-global way to disable the redaction of fully-matched data-structures in matcher-combinator output."
  []
  (set-use-redaction! false))


;; Disable special ANSI color characters in output
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- set-use-color! [v]
  #?(:clj (alter-var-root #'ansi-color/*use-color* (constantly v))
     :cljs (set! ansi-color/*use-color* v)))

(defn enable-ansi-color!
  "Thread-global way to enable the usage of ANSI color codes in matcher-combinator output."
  []
  (set-use-color! true))

(defn disable-ansi-color!
  "Thread-global way to disable the usage of ANSI color codes in matcher-combinator output."
  []
  (set-use-color! false))
