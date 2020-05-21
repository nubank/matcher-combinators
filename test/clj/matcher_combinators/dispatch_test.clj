(ns matcher-combinators.dispatch-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.core :as core]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.standalone :as s]))

(def chunked-seq (seq [1]))

(deftest chunked-seq-remap
  (is (not (s/match? chunked-seq [1 2 3])))

  (is (dispatch/wrap-match-with
       {clojure.lang.PersistentVector$ChunkedSeq core/->EmbedsSeq}
       (s/match? chunked-seq [1 2 3]))))

(deftest array-seq
  (is (not (s/match? (clojure.lang.ArraySeq/create (into-array [1 2]))
                     [2 2])))
  (is (s/match? (clojure.lang.ArraySeq/create (into-array [1 2]))
                [1 2])))
