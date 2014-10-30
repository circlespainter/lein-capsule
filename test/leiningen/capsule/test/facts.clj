(ns leiningen.capsule.test.facts
  (:use midje.sweet)
  (:require
    [leiningen.test.helper :as lh]))

; TODO Check that reusing this works and then ask technomancy to make it public
(def read-project @#'lh/read-test-project)

; Based on https://github.com/marick/Midje/issues/231#issuecomment-22465088
(facts "test projects"
       ; TODO Implement
       ; For each dir in "test_projects":
       ; 1. Read project
       ; 2. lein capsule
       ; 3. For each build capsule, read spec of expected manifest, jars and output and check them through a dynamic fact
       ; 4. lein clean
)