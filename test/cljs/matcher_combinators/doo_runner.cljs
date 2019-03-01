(ns matcher-combinators.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            matcher-combinators.foo))

(enable-console-print!)

(doo-tests 'matcher-combinators.foo)
