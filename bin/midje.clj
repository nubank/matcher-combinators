(ns midje
  (:require matcher-combinators.midje-test
            midje.repl))

(def midje-test-namespaces ['matcher-combinators.midje-test])

(defn test! [& args]
  (let [results (apply midje.repl/load-facts midje-test-namespaces)]
    (System/exit (if (zero? (:failures results))
                   0
                   1))))
