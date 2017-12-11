(defproject matcher-combinators "0.1.0-SNAPSHOT"
  :description "Library for creating matcher combinator to compare nested data structures"
  :url "https://github.com/rafaeldff/matcher-combinators"
  :license {:name "Proprietary"}

  :repositories [["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :plugins [[lein-midje "3.2.1"]
            [lein-ancient "0.6.14"]]
  :dependencies [[org.clojure/clojure "1.9.0-RC2"]]
  :profiles {:dev {:dependencies [[colorize "0.1.1" :exclusions [org.clojure/clojure]]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.9.0" :exclusions [org.clojure/clojure]]]}})
