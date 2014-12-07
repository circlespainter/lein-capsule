; lein-capsule: a Leiningen plugin for Parallel Universe's Capsule.
;
; Copyright (C) 2014 Fabio Tudone. All rights reserved.
;
; This program and the accompanying materials are dual-licensed under
; either the terms of the Eclipse Public License v1.0 as published by
; the Eclipse Foundation
;
;   or (per the licensee's choosing)
;
; under the terms of the GNU Lesser General Public License version 3.0
; as published by the Free Software Foundation.

; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns ^{ :author "circlespainter" :internal true } leiningen.capsule
  "Main entry for lein-capsule plugin"

  (:require
    [leiningen.core.project :as project]

    [leiningen.core.main :as main]

    [leiningen.jar :as jar]

    [leiningen.capsule.utils :as cutils]
    [leiningen.capsule.build :as cbuild]
    [leiningen.capsule.spec :as cspec]))

(defn capsule
  "Creates specified capsules for the project"
  [project]

  (let [default-mode (cutils/get-capsule-default-mode project)
        project (cspec/validate-and-normalize project)
        project-jar-files (jar/jar project)]
    (doseq [[capsule-type-name capsule-type-spec] (cutils/get-capsule-types project)]
      (let [_
              (main/debug (str "\n\n*** Creating " (name capsule-type-name) " capsule "
                               (cutils/get-capsules-output-dir project) "/" (:name capsule-type-spec) "\n"))

            project (cutils/get-project-without-default-mode project)
            ; TODO It seems to work but check it is the correct way to merge parts
            ; Merge potential overrides before processing: default mode and current build configuration (type)
            project
              (project/merge-profiles project
                                      [(if default-mode {:capsule default-mode} {}) {:capsule capsule-type-spec}])
            project (cspec/capsulize project)]
        (cbuild/build-capsule project-jar-files project capsule-type-name capsule-type-spec)))))