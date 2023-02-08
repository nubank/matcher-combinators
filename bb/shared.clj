(ns shared
  "Shared code between build.clj and bb tasks"
  (:require [clojure.edn :as edn]))

(defn version-map []
  (edn/read-string (slurp "version.edn")))

(defn version []
  (let [{:keys [major minor release qualifier]} (version-map)]
    (format "%s.%s.%s%s"
            major minor release (if qualifier
                                  (str "-" (name qualifier))
                                  ""))))
