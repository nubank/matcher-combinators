(ns matcher-combinators.config
  "Global output behavior configurations"
  (:require [matcher-combinators.ansi-color :as ansi-color]))

;; Abbreviating match results to only include mismatched data in the output
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:dynamic true
       :doc "thread-local way to control, via `binding`, the abbreviation of fully-matched data-structures in the matcher-combinator output"}
  *use-abbreviation*
  false)

(defn- set-use-abbreviation!
  "internal function, use matcher-combinators.config/{enable|disable}-abbreviation!"
  [v]
  #?(:clj (alter-var-root #'*use-abbreviation* (constantly v))
     :cljs (set! *use-abbreviation* v)))

(defn enable-abbreviation!
  "**Experimental, subject to change**
  Thread-global way to enable the abbreviation of fully-matched data-structures in matcher-combinator output."
  []
  (set-use-abbreviation! true))

(defn disable-abbreviation!
  "**Experimental, subject to change**
  Thread-global way to disable the abbreviation of fully-matched data-structures in matcher-combinator output."
  []
  (set-use-abbreviation! false))


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
