(defproject nubank/matcher-combinators "0.8.4"
  :description "Library for creating matcher combinator to compare nested data structures"
  :url "https://github.com/nubank/matcher-combinators"
  :license {:name "Apache License, Version 2.0"}

  :repositories [["central" {:url "https://repo1.maven.org/maven2/" :snapshots false}]
                 ["clojars" {:url "https://clojars.org/repo/"}]]

  :cljfmt {:indents {facts    [[:block 1]]
                     fact     [[:block 1]]
                     fdef     [[:block 1]]
                     provided [[:inner 0]]
                     tabular  [[:inner 0]]}}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [org.clojure/math.combinatorics "0.1.5"]
                 [midje "1.9.6" :exclusions [org.clojure/clojure]]]

  :test-paths ["test/clj"]
  :source-paths ["src/cljc" "src/cljs" "src/clj"]

  :profiles {:dev {:plugins [[lein-midje "3.2.1"]
                             [lein-cljfmt "0.5.7"]
                             [lein-cljsbuild "1.1.7"]
                             [lein-kibit "0.1.6"]
                             [lein-ancient "0.6.15"]
                             [lein-doo "0.1.11"]]
                   :dependencies [[org.clojure/test.check "0.10.0-alpha3"]
                                  [org.clojure/clojurescript "1.10.520"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}}

  :aliases {"lint"     ["do" "cljfmt" "check," "kibit"]
            "lint-fix" ["do" "cljfmt" "fix," "kibit" "--replace"]
            "test-clj" ["all" "do" ["test"] ["check"]]
            "test-phantom" ["doo" "phantom" "test"]
            "test-advanced" ["doo" "phantom" "advanced-test"]
            "test-node-watch" ["doo" "node" "node-test"]
            "test-node" ["doo" "node" "node-test" "once"]}
  ;; Below, :process-shim false is workaround for <https://github.com/bensu/doo/pull/141>
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src/cljc" "src/cljs" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main matcher-combinators.doo-runner
                                   :optimizations :none
                                   :process-shim false}}
                       {:id "advanced-test"
                        :source-paths ["src/cljc" "src/cljs" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/advanced_out/test.js"
                                   :output-dir "target/advanced_out"
                                   :main matcher-combinator.doo-runner
                                   :optimizations :advanced
                                   :process-shim false}}
                       ;; Node.js requires :target :nodejs, hence the separate
                       ;; build configuration.
                       {:id "node-test"
                        :source-paths ["src/cljc" "src/cljs" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/node_out/test.js"
                                   :output-dir "target/node_out"
                                   :main matcher-combinators.doo-runner
                                   :optimizations :none
                                   :target :nodejs
                                   :process-shim false}}]})
