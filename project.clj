(defproject matcher-combinators "0.1.0-SNAPSHOT"
  :description "Library for creating matcher combinator to compare nested data structures"
  :url "https://github.com/rafaeldff/matcher-combinators"
  :license {:name "Proprietary"}

  :repositories [["central" {:url "http://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :plugins [[lein-midje "3.2.1"]
            [s3-wagon-private "1.3.0"]]
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev {:dependencies [[colorize "0.1.1" :exclusions [org.clojure/clojure]]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.9.0-alpha13" :exclusions [org.clojure/clojure]]]}})
