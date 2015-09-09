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

(ns ^{ :author "circlespainter" :internal true } leiningen.capsule.build
  "Creates the specified capsules for the project"

  (:require
    [clojure.string :as cstr]

    [cemerick.pomegranate.aether :as aether]

    [leiningen.core.main :as main]

    [leiningen.capsule.aether :as aether-ported]
    [leiningen.capsule.utils :as cutils]
    [leiningen.capsule.consts :as cc])

  (:import
    (capsule Dependencies)
    (co.paralleluniverse.capsule Jar)
    (java.io FileOutputStream)
    (java.nio.file Paths Files StandardOpenOption OpenOption)
    (java.util.zip ZipInputStream)))

(defn- make-aether-dep [lein-style-spec]
  "Builds an Aether dependency from a Leinigen one"
  (Dependencies/toCapsuleDependencyString (@#'aether-ported/dependency lein-style-spec)))

(defn- jvm-lein-deps [project]
  "Extracts Leiningen dependencies from a project"
  (:dependencies project))

(defn- jvm-lein-agent-deps [project]
  "Extracts Leiningen agent dependencies from a project"
  (:java-agents project))

(defn- capsule-deps [project path & [mode-keyword]]
  "Extracts Capsule-spec dependencies from a specific path location"
  (get-in project (cc/mode-aware-path project path mode-keyword)))

(defn- native-mac-capsule-deps [project & [mode-keyword]]
  "Extracts Capsule-spec Mac native dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-native-mac mode-keyword))

(defn- native-linux-capsule-deps [project & [mode-keyword]]
  "Extracts Capsule-spec Linux native dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-native-linux mode-keyword))

(defn- native-windows-capsule-deps [project & [mode-keyword]]
  "Extracts Capsule-spec Windows native dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-native-windows mode-keyword))

(defn- jvm-computed-capsule-deps [project & [mode-keyword]]
  "Computes JVM dependencies based on both Leiningen and Capsule-spec"
  (let [p1
          (cutils/diff
            (jvm-lein-deps project)
            (cutils/get-diff-section project cc/path-maven-dependencies-artifacts-jvm mode-keyword))
        p2
          (cutils/diff
            (jvm-lein-agent-deps project)
            (cutils/get-diff-section project cc/path-runtime-java-agents mode-keyword))
        p3
          (get-in project (cc/mode-aware-path project cc/path-runtime-native-agents mode-keyword))]
    (concat p1 p2 p3)))

(defn- dependency-matches-exception [dep dep-exc]
  "Tells if the dependecies match"
  (or
    (= dep-exc dep)
    (and
      (coll? dep-exc) (coll? dep)
      (<= 1 (count dep-exc) (count dep))
      (= (first dep-exc) (first dep)))))

(defn- dependency-matches-any-exceptions [dep exceptions]
  "Tells if the dependency matches any in the given collection"
  (some #(dependency-matches-exception dep %) exceptions))

(defn- filter-deps [deps exceptions exceptions-mode]
  "Filter dependencies based on exceptions specification"
  (cond
    (and (= exceptions-mode :only)(not (seq exceptions)))
      []
    (and deps (seq exceptions))
      (do
        (filter
          #(let [match (dependency-matches-any-exceptions % exceptions)]
            (case (or exceptions-mode :except)
              (:except :xexcept) (not match)
              (:only :xonly) match
              :else true))
          deps))
    :else deps))

(defn- make-aether-repo [lein-repo]
  "Builds an aether repository from a Leiningen repository"
  ; TODO Remove trick once https://github.com/cemerick/pomegranate/pull/69 is merged
  (Dependencies/toCapsuleRepositoryString (@#'aether-ported/make-repository lein-repo nil)))

(defn- lein-repos [project]
  "Extracts Leiningen repositories from project"
  (:repositories project))

(defn- is-non-default-mode-and-does-not-contribute-to-path [project path & [mode-keyword]]
  "Tests if a mode is non-default and does not contribute something to a given spec path"
  (and mode-keyword
       (cc/non-default-mode project mode-keyword)
       (not (get-in project (cc/mode-aware-path project path mode-keyword)))))

(defn- manifest-put-mvn-repos [project & [mode-keyword]]
  "Adds repositories to the Capsule manifest"
  (if (is-non-default-mode-and-does-not-contribute-to-path
        project cc/path-maven-dependencies-repositories mode-keyword)
    project
    (let [lein-prj-repos (lein-repos project)
          aether-prj-repos (map make-aether-repo lein-prj-repos)
          repos
            (cutils/diff
              aether-prj-repos
              (cutils/get-diff-section project cc/path-maven-dependencies-repositories mode-keyword))
          contains-clojars?
            (some
              #(.contains % "clojars.org/repo")
              repos)
          patched-repos
            (if contains-clojars?
              repos
              (concat repos [cc/clojars-repo-url]))]
      (cutils/add-to-manifest
        project
        "Repositories"
        (cstr/join " " patched-repos)
        mode-keyword))))

(defn- manifest-put-mvn-deps [project & [mode-keyword exceptions exceptions-mode]]
  "Specifies some, all or all except some project dependencies for Capsule to retrieve at jar boot"
  (let [make-deps-string
          (fn [project path deps-fn]
            (if (is-non-default-mode-and-does-not-contribute-to-path project path mode-keyword)
              ""
              (cstr/join
                " " (map make-aether-dep (filter-deps (deps-fn project mode-keyword) exceptions exceptions-mode)))))
        add-deps
          (fn [project path mf-entry-name deps-fn]
            (if (is-non-default-mode-and-does-not-contribute-to-path project path mode-keyword)
              project
              (cutils/add-to-manifest project mf-entry-name (make-deps-string project path deps-fn) mode-keyword)))]
    (main/debug
      (str "Maven JVM deps (mode " mode-keyword "): "
           (make-deps-string project cc/path-maven-dependencies-artifacts-jvm jvm-computed-capsule-deps)))
    (main/debug
      (str "Maven native Mac deps (mode " mode-keyword "): "
           (make-deps-string project cc/path-maven-dependencies-artifacts-native-mac native-mac-capsule-deps)))
    (main/debug
      (str "Maven native Linux deps (mode " mode-keyword "): "
           (make-deps-string project cc/path-maven-dependencies-artifacts-native-linux native-linux-capsule-deps)))
    (main/debug
      (str "Maven native Windows deps (mode " mode-keyword "): "
           (make-deps-string project cc/path-maven-dependencies-artifacts-native-windows native-windows-capsule-deps)))
    (-> project
        (add-deps cc/path-maven-dependencies-artifacts-jvm "Dependencies" jvm-computed-capsule-deps)
        (add-deps cc/path-maven-dependencies-artifacts-native-mac "Native-Dependencies-Mac" native-mac-capsule-deps)
        (add-deps
          cc/path-maven-dependencies-artifacts-native-linux "Native-Dependencies-Linux" native-linux-capsule-deps)
        (add-deps
          cc/path-maven-dependencies-artifacts-native-windows
          "Native-Dependencies-Windows" native-windows-capsule-deps))))

(defn- get-dep-files [project deps]
  "Given a seq of Leiningen-style dependencies, retrieves files for the whole transitive dependency graph"
  (aether/dependency-files
    (aether/resolve-dependencies
      :coordinates deps
      :repositories (lein-repos project))))

(defn- retrieve-and-insert-mvn-deps [project capsule & [mode-keyword exceptions exceptions-mode]]
  "Retrieves some, all or all except some project dependencies and inserts them in the capsule jar under a
   Capsule-compliant tree structure, leaving the remaining ones for Capsule to retrieve at jar boot"
  (let [make-deps
          (fn [deps-fn path]
            (if (is-non-default-mode-and-does-not-contribute-to-path project path mode-keyword)
              [] (filter-deps (deps-fn project mode-keyword) exceptions exceptions-mode)))
        jvm (make-deps jvm-computed-capsule-deps cc/path-maven-dependencies-artifacts-jvm )
        native-mac (make-deps native-mac-capsule-deps cc/path-maven-dependencies-artifacts-native-mac)
        native-linux (make-deps native-linux-capsule-deps cc/path-maven-dependencies-artifacts-native-linux)
        native-windows (make-deps native-windows-capsule-deps cc/path-maven-dependencies-artifacts-native-windows)]
    (main/debug (str "Embedding JVM deps: " jvm " (mode " mode-keyword ")"))
    (main/debug (str "Embedding native Mac deps: " native-mac " (mode" mode-keyword ")"))
    (main/debug (str "Embedding native Linux deps: " native-linux " (mode" mode-keyword ")"))
    (main/debug (str "Embedding native Windows deps: " native-windows " (mode" mode-keyword ")"))
    (doseq [dep-file (get-dep-files project (concat jvm native-mac native-linux native-windows))]
      (.addEntry capsule (.getName dep-file) dep-file))))

(defn- write-manifest [capsule project]
  "Writes the Capsule manifest"
  (doseq [[k v] (get-in project (cc/capsule-manifest-path project))]
    (cond
      (string? v)
        (do
          (main/debug "Setting main manifest attribute:" k v)
          (.setAttribute capsule k v))
      (coll? v)
        (doseq [[section-k section-v] v]
          (main/debug "Setting section manifest attribute:" (name k) section-k section-v)
          (.setAttribute capsule (name k) section-k section-v)))))

(defn- capsule-output-stream [project spec]
  "Creates a capsule output stream"
  (let [out-file-name (str (cutils/get-capsules-output-dir project) "/" (:name spec))]
    (clojure.java.io/make-parents out-file-name)
    (FileOutputStream. out-file-name)))

(defn- copy-jar-files-into-capsule [project-jar-files capsule]
  "Copy the 'leniningen.jar/jar'-produced project jars in the capsule's root"
  (doseq [jar-path (map #(Paths/get % (make-array String 0)) (vals project-jar-files))]
    (.addEntry capsule (.getFileName jar-path) jar-path)))

(defn- extract-jar-contents-to-capsule [project-jar-files capsule]
  "Extracts the 'leniningen.jar/jar'-produced project jars in the capsule's root"
  (doseq [jar-zis
            (map #(ZipInputStream.
                   (Files/newInputStream
                     (Paths/get % (make-array String 0))
                     (into-array OpenOption [StandardOpenOption/READ])))
                 (vals project-jar-files))]
    (.addEntries capsule nil jar-zis)))

(defn self-contained [project base-type excepts]
  (not (or
    (not= base-type :fat)
    (seq excepts)
    (seq (cutils/execution-boot-artifacts project)))))

; TODO If possible bettet to avoid special handling depending on default mode presence
(defn- build-mixed [base-type project-jar-files project spec & [excepts]]
  "Builds a mixed capsule: will add every dependency to the manifest or will insert/embed it"
  (let [capsule (.setOutputStream (Jar.) (capsule-output-stream project spec))
        project
          ; If there isn't a default mode, add manifest repos without passing any mode
          (if (cutils/get-capsule-default-mode-name project) project (manifest-put-mvn-repos project))
        project
          ; If there isn't a default mode, add manifest deps without passing any mode
          (if (cutils/get-capsule-default-mode-name project)
            project
            (manifest-put-mvn-deps project nil excepts (case base-type :fat :only :thin :except)))
        project
          ; For each mode, add relevant overriding manifest repos and deps
          (reduce-kv
            (fn [project k _]
              (-> project
                  (manifest-put-mvn-repos k)
                  (manifest-put-mvn-deps k excepts (case base-type :fat :only :thin :except))))
            project
            (cutils/get-modes project))
        project
          ; If not thin, needs extracting jars
          (if (or (not= base-type :thin) (seq excepts))
            (cutils/add-to-manifest project "Extract-Capsule" "true")
            project)
        project
          (if (not (self-contained project base-type excepts))
            (cutils/add-to-manifest project "Caplets" "MavenCapsule")
            project)]

    ; Write capsule manifest
    (write-manifest capsule project)

    ; If there isn't a default mode, insert jars without passing any mode
    (if (not (cutils/get-capsule-default-mode-name project))
      (retrieve-and-insert-mvn-deps project capsule nil excepts (case base-type :fat :except :thin :only)))

    ; For each mode, insert its jars
    (reduce-kv
      (fn [project k _]
        (retrieve-and-insert-mvn-deps project capsule k excepts (case base-type :fat :except :thin :only)))
      project
      (cutils/get-modes project))

    ; If it's not a completely self-contained fat Capsule, extract full Capsule's jar including the dependency manager
    (if (not (self-contained project base-type excepts))
      (do
        (extract-jar-contents-to-capsule
          {:capsule-maven-jar
           (.getAbsolutePath (first (get-dep-files project [['co.paralleluniverse/capsule-maven cc/capsule-version]])))}
          capsule))
      ; Else the Capsule class will be enough
      (.addClass capsule (Class/forName "Capsule")))

    ; If it's a fully thin capsule without any exceptions, extract project jars
    (if (and (= base-type :thin) (not (seq excepts)))
      (extract-jar-contents-to-capsule project-jar-files capsule)
      ; Else copy them
      (copy-jar-files-into-capsule project-jar-files capsule))

    ; Finally close capsule's stream
    ; TODO maybe there's a loan way to do it
    (.close capsule)))

(defn- build-thin-spec [project-jar-files project spec]
  "Builds a thin capsule"
  (build-mixed :thin project-jar-files project spec))

(defn- build-fat-spec [project-jar-files project spec]
  "Builds a fat capsule"
  (build-mixed :fat project-jar-files project spec))

(defn- build-thin-except-clojure-spec [project-jar-files project spec]
  "Builds a thin capsule embedding clojure"
  (build-mixed :thin project-jar-files project spec '[[org.clojure/clojure]]))

(defn- build-fat-except-clojure-spec [project-jar-files project spec]
  "Builds a fat capsule excluding clojure"
  (build-mixed :fat project-jar-files project spec '[[org.clojure/clojure]]))

; TODO Case for multi?
(defn- build-mixed-spec [project-jar-files project spec]
  "Builds a mixed capsule"
  (cond
    (:fat-except spec) (build-mixed :fat project-jar-files project spec (:fat-except spec))
    (:thin-except spec) (build-mixed :thin project-jar-files project spec (:thin-except spec))
    :else
      (throw (RuntimeException. "Unexpected mixed capsule type specified, use either :fat-except or :thin-except"))))

; TODO Case for multi?
(defn ^:internal build-capsule [project-jar-files project capsule-type-name capsule-type-spec]
  "Builds the capsule(s) of a given type using the pre-processed project map"
  (main/debug (str "\nBuilding "
                   (name capsule-type-name)
                   " capsule "
                   (cutils/get-capsules-output-dir project) "/" (:name capsule-type-spec)
                   ", current manifest " (get-in project (cc/capsule-manifest-path project)) "\n"))
  ((case capsule-type-name
     :thin build-thin-spec
     :fat build-fat-spec
     :thin-except-clojure build-thin-except-clojure-spec
     :fat-except-clojure build-fat-except-clojure-spec
     :mixed build-mixed-spec)
    project-jar-files project capsule-type-spec)
  (main/info (str "Created " (name capsule-type-name) " capsule "
                  (cutils/get-capsules-output-dir project) "/" (:name capsule-type-spec) "")))
