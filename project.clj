(defproject nubank/matcher-combinators "3.6.0"
  :description "Library for creating matcher combinator to compare nested data structures"
  :url "https://github.com/nubank/matcher-combinators"
  :license {:name "Apache License, Version 2.0"}

  :repositories [["publish" {:url "https://clojars.org/repo"
                             :username :env/clojars_username
                             :password :env/clojars_passwd
                             :sign-releases false}]]

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/spec.alpha "0.3.218"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [midje "1.10.7" :exclusions [org.clojure/clojure]]]

  :source-paths ["src/clj" "src/cljc"]
  :test-paths   ["test/clj" "test/cljc"]

  :profiles {:dev {:plugins [[com.github.clojure-lsp/lein-clojure-lsp "1.3.11"]
                             [lein-project-version "0.1.0"]
                             [lein-midje "3.2.1"]
                             [lein-cljsbuild "1.1.7"]
                             [lein-ancient "0.6.15"]
                             [lein-doo "0.1.11"]]
                   :dependencies [[org.clojure/test.check "1.1.1"]
                                  [org.clojure/clojurescript "1.11.4"]
                                  [org.clojure/core.rrb-vector "0.1.2"]
                                  [orchestra "2021.01.01-1"]]
                   :source-paths ["dev"]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}}

  :aliases {"format"          ["clojure-lsp" "format" "--dry"]
            "format-fix"      ["clojure-lsp" "format"]
            "clean-ns"        ["clojure-lsp" "clean-ns" "--dry"]
            "clean-ns-fix"    ["clojure-lsp" "clean-ns"]
            "lint"            ["do" ["format"] ["clean-ns"]]
            "lint-fix"        ["do" ["format-fix"] ["clean-ns-fix"]]
            "test-phantom"    ["doo" "phantom" "test"]
            "test-advanced"   ["doo" "phantom" "advanced-test"]
            "test-node-watch" ["doo" "node" "node-test"]
            "test-node"       ["doo" "node" "node-test" "once"]}
  ;; Below, :process-shim false is workaround for <https://github.com/bensu/doo/pull/141>
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src/cljs" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main matcher-combinators.doo-runner
                                   :optimizations :none
                                   :process-shim false}}
                       {:id "advanced-test"
                        :source-paths ["src/cljs" "test/cljs"]
                        :compiler {:output-to "target/advanced_out/test.js"
                                   :output-dir "target/advanced_out"
                                   :main matcher-combinator.doo-runner
                                   :optimizations :advanced
                                   :process-shim false}}
                       ;; Node.js requires :target :nodejs, hence the separate
                       ;; build configuration.
                       {:id "node-test"
                        :source-paths ["src/cljs" "test/cljs"]
                        :compiler {:output-to "target/node_out/test.js"
                                   :output-dir "target/node_out"
                                   :main matcher-combinators.doo-runner
                                   :optimizations :none
                                   :target :nodejs
                                   :process-shim false}}]})
