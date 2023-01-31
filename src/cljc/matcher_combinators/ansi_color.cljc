(ns matcher-combinators.ansi-color
  "Colorize console text. Mostly copied from Bruce Hauman's Figwheel project"
  (:require [clojure.string :as str]))

(def ^{:dynamic true
       :doc "thread-local way to control, via `binding`, the usage of ANSI color codes in matcher-combinator output"}
  *use-color*
  true)

(defn- set-use-color! [v]
  #?(:clj (alter-var-root #'*use-color* (constantly v))
     :cljs (set! *use-color* v)))

(defn enable!
  "Thread-global way to enable the usage of ANSI color codes in matcher-combinator output."
  []
  (set-use-color! true))

(defn disable!
  "Thread-global way to disable the usage of ANSI color codes in matcher-combinator output."
  []
  (set-use-color! false))

(def ANSI-CODES
  {:reset              "[0m"
   :bright             "[1m"
   :blink-slow         "[5m"
   :underline          "[4m"
   :underline-off      "[24m"
   :inverse            "[7m"
   :inverse-off        "[27m"
   :strikethrough      "[9m"
   :strikethrough-off  "[29m"

   :default "[39m"
   :white   "[37m"
   :black   "[30m"
   :red     "[31m"
   :green   "[32m"
   :blue    "[34m"
   :yellow  "[33m"
   :magenta "[35m"
   :cyan    "[36m"

   :bg-default "[49m"
   :bg-white   "[47m"
   :bg-black   "[40m"
   :bg-red     "[41m"
   :bg-green   "[42m"
   :bg-blue    "[44m"
   :bg-yellow  "[43m"
   :bg-magenta "[45m"
   :bg-cyan    "[46m"})

(defn- ansi-code [code]
  (when (and *use-color* code)
    (str \u001b code)))

(defn- style* [s & codes]
  (str (str/join (map ansi-code codes)) s (ansi-code "[0m")))

(defn style [s & codes]
  (apply style* s (map ANSI-CODES codes)))

(defn set-color [color]
  (print (str (ansi-code (ANSI-CODES color)))))

(defn reset []
  (print (str (ansi-code (ANSI-CODES :reset)))))
