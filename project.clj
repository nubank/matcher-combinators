(defproject nubank/matcher-combinators "0.1.6-SNAPSHOT"
  :description "Library for creating matcher combinator to compare nested data structures"
  :url "https://github.com/nubank/matcher-combinators"
  :license {:name "Apache License, Version 2.0"}

  :repositories [["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :plugins [[lein-midje "3.2.1"]
            [lein-ancient "0.6.14"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [midje "1.9.2-alpha2" :exclusions [org.clojure/clojure]]]

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
