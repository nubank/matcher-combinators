(ns build
  (:require [shared]
            [deps-deploy.deps-deploy :as dd]
            [clojure.tools.build.api :as b]))

(def lib 'nubank/matcher-combinators)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def version (shared/version))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn jar []
  (b/delete {:path class-dir})
  (b/delete {:path jar-file})
  (println "Writing pom:" jar-file)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :scm {:url "http://github.com/nubank/matcher-combinators"
                      :connection "scm:git:git://github.com/nubank/matcher-combinators.git"
                      :developerConnection "scm:git:ssh://git@github.com/nubank/matcher-combinators.git"
                      :tag (str "v" version)}
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src/clj" "src/cljc" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy!
  "Deploy built jar to clojars"
  [& _args]
  (jar)
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
