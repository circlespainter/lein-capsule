; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns ^{ :author "circlespainter" :internal true } leiningen.capsule
  "Main entry for lein-capsule plugin"

  (:require
    [leiningen.core.project :as project]

    [leiningen.compile :as compile]
    [leiningen.clean :as clean]

    [leiningen.capsule.utils :as cutils]
    [leiningen.capsule.build :as cbuild]
    [leiningen.capsule.spec :as cspec]))

(defn capsule
  "Creates specified capsules for the project"
  [project & args]
  (let [default-profile (cutils/get-capsule-default-profile project)
        project (cspec/validate-and-normalize project)]
    (doseq [[capsule-type-name v] (cutils/get-capsule-types project)]
      (let [project (cutils/get-project-without-default-profile project)
            project (project/merge-profiles project [{:capsule default-profile} {:capsule v}])
            project (cspec/capsulize project)]
        (clean/clean project) ; TODO Improve: avoid cleaning if/when possible
        (apply compile/compile (cons project args))
        (cbuild/build-capsule project capsule-type-name v)))))