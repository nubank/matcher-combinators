(defproject nubank/matcher-combinators "0.3.4"
  :description "Library for creating matcher combinator to compare nested data structures"
  :url "https://github.com/nubank/matcher-combinators"
  :license {:name "Apache License, Version 2.0"}

  :repositories [["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :cljfmt {:indents {facts              [[:block 1]]
                     fact               [[:block 1]]
                     provided           [[:inner 0]]
                     tabular            [[:inner 0]]}}

  :plugins [[lein-midje "3.2.1"]
            [lein-cljfmt "0.5.7"]
            [lein-kibit "0.1.6"]
            [lein-ancient "0.6.15"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/math.combinatorics "0.1.4"]
                 [midje "1.9.3-alpha2" :exclusions [org.clojure/clojure]]]

  :aliases {"lint"            ["do" "cljfmt" "check," "kibit"]
            "lint-fix"        ["do" "cljfmt" "fix," "kibit" "--replace"]}

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.10.0-alpha3"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}})
