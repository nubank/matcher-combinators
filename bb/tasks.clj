(ns tasks
  "Functions used in bb.edn"
  (:require [babashka.tasks :refer [shell]]
            [shared :refer [version]]))

(defn release []
  (shell (format "git tag \"%s\"" (version)))
  (shell (format "git push origin \"%s\"" (version))))
