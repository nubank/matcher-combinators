(defproject matcher-combinators "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Proprietary"}

  :repositories [["nu-maven" {:url "s3p://nu-maven/releases/"}]
                 ["central" {:url "http://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :plugins [[lein-midje "3.1.3"]
            [s3-wagon-private "1.3.0"]]
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:dev {:dependencies [;[ns-tracker "0.3.0"]
                                  [org.clojure/tools.namespace "0.2.11"]

                                  [midje "1.9.0-alpha8" :exclusions [org.clojure/clojure]]
                                  ;[org.clojure/java.classpath "0.2.3"]
                                  [org.clojure/test.check "0.9.0"]
                                  [nu-test "0.3.4"]
                                  [colorize "0.1.1" :exclusions [org.clojure/clojure]]
                                  ]}}

  )
