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
    (co.paralleluniverse.capsule.build Dependencies)
    (co.paralleluniverse.capsule Jar)
    (java.io File FileOutputStream)
    (java.nio.file Path Paths Files StandardOpenOption OpenOption)
    (java.util.zip ZipInputStream)
    (java.util UUID)))

(defn- make-aether-dep [lein-style-spec]
  "Builds an Aether dependency from a Leinigen one"
  (Dependencies/toCapsuleDependencyString (@#'aether-ported/dependency lein-style-spec)))

(defn- jvm-lein-deps [project]
  "Extracts Leiningen dependencies from a project"
  (:dependencies project))

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
  (cutils/diff
    (jvm-lein-deps project)
    (cutils/diff-section project cc/path-maven-dependencies-artifacts-jvm mode-keyword)))

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

(defn- manifest-put-mvn-repos [project & [mode-keyword]]
  "Adds repositories to the Capsule manifest"
  (let [patched-repos
          (cons cc/clojars-repo-url
                (cutils/diff
                  (map make-aether-repo (lein-repos project))
                  (cutils/diff-section project cc/path-maven-dependencies-repositories mode-keyword)))]
    (cutils/add-to-manifest
      project
      "Repositories"
      (cstr/join " " patched-repos)
      mode-keyword)))

(declare retrieve-and-insert-mvn-deps)

(defn- manifest-put-mvn-deps [project & [mode-keyword exceptions exceptions-mode]]
  "Specifies some, all or all except some project dependencies for Capsule to retrieve at jar boot, inserting the
  remaining ones in the capsule jar under a Capsule-compliant tree structure"
  (let [make-deps-string
          #(cstr/join " " (map make-aether-dep (filter-deps (% project mode-keyword) exceptions exceptions-mode)))
        add-mf #(cutils/add-to-manifest %1 %2 %3 mode-keyword)

        jvm (make-deps-string jvm-computed-capsule-deps)
        native-mac (make-deps-string native-mac-capsule-deps)
        native-linux (make-deps-string native-linux-capsule-deps)
        native-win (make-deps-string native-windows-capsule-deps)]
    (-> project
        (add-mf "Dependencies" jvm)
        (add-mf "Native-Dependencies-mac" native-mac)
        (add-mf "Native-Dependencies-Linux" native-linux)
        (add-mf "Native-Dependencies-Win" native-win))))

(defn- get-dep-files [project deps]
  "Given a seq of Leiningen-style dependencies, retrieves files for the whole transitive dependency graph"
  (aether/dependency-files
    (aether/resolve-dependencies
      :coordinates deps
      :repositories (lein-repos project))))

(defn- retrieve-and-insert-mvn-deps [project capsule & [mode-keyword exceptions exceptions-mode]]
  "Retrieves some, all or all except some project dependencies and inserts them in the capsule jar under a
   Capsule-compliant tree structure, leaving the remaining ones for Capsule to retrieve at jar boot"
  (let [make-deps #(filter-deps (% project mode-keyword) exceptions exceptions-mode)

        jvm (make-deps jvm-computed-capsule-deps)
        native-mac (make-deps native-mac-capsule-deps)
        native-linux (make-deps native-linux-capsule-deps)
        native-win (make-deps native-windows-capsule-deps)]
    (doseq [dep-file (get-dep-files project (concat jvm native-mac native-linux native-win))]
      (.addEntry capsule (.getName dep-file) dep-file))))

(defn- write-manifest [capsule project]
  "Writes the Capsule manifest"
  (doseq [[k v] (get-in project (cc/capsule-manifest-path project))]
    (cond
      (string? v)
        (.setAttribute capsule k v)
      (coll? v)
        (doseq [[section-k section-v] v]
          (.setAttribute capsule (name k) section-k section-v)))))

(defn- get-capsules-output-dir [project]
  "Computes the capsules output dir starting from the project target folder based on specification or sensible defaults"
  (str (:target-path project) "/" (or (.toString (get-in project (cons :capsule cc/path-output-dir))) "capsules")))

(defn- capsule-output-stream [project spec]
  "Creates a capsule output stream"
  (let [out-file-name (str (get-capsules-output-dir project) "/" (:name spec))]
    (clojure.java.io/make-parents out-file-name)
    (FileOutputStream. out-file-name)))

(defn- copy-jar-files-into-capsule [jar-files capsule]
  "Copy the 'leniningen.jar/jar'-produced project jars in the capsule's root"
  (doseq [jar-path (map #(Paths/get % (make-array String 0)) (vals jar-files))]
    (.addEntry capsule (.getFileName jar-path) jar-path)))

(defn- extract-jar-contents-to-capsule [jar-files capsule]
  "Extracts the 'leniningen.jar/jar'-produced project jars in the capsule's root"
  (doseq [jar-zis
            (map #(ZipInputStream.
                   (Files/newInputStream
                     (Paths/get % (make-array String 0))
                     (into-array OpenOption [StandardOpenOption/READ])))
                 (vals jar-files))]
    (.addEntries capsule nil jar-zis)))

(defn- build-mixed [base-type jar-files project spec & [excepts]]
  "Builds a mixed capsule"
  ; Will add every dependency to the manifest
  ; TODO De-uglify and cleanup, empty bindings for side-effects are smelly
  (let [capsule (Jar.)
        _ (.setOutputStream capsule (capsule-output-stream project spec))
        project
          (reduce-kv
            (fn [project k _]
              (-> project
                  (manifest-put-mvn-repos k)
                  (manifest-put-mvn-deps k excepts (case base-type :fat :only :thin :except))))
            project
            (get-in project cc/path-modes))
        project
          (if (or (not= base-type :thin) (seq excepts))
            (cutils/add-to-manifest project "Extract-Capsule" "true")
            project)
        _ (write-manifest capsule project)
        _ ; Doing only for side-effects
          (reduce-kv
            (fn [project k _]
              (retrieve-and-insert-mvn-deps project capsule k excepts (case base-type :fat :except :thin :only)))
            project
            (get-in project cc/path-modes))]

    (if (or
          (not= base-type :fat)
          (seq excepts)
          (seq (cutils/execution-boot-artifacts project))
          (cutils/has-artifact-agents project))
      ; Extract full jar to capsule, including the dependency manager
      (extract-jar-contents-to-capsule
        {:capsule-jar (.getAbsolutePath (first (get-dep-files project [['co.paralleluniverse/capsule cc/capsule-version]])))}
        capsule)
      ; Else the Capsule class will be enough
      (.addClass capsule (Class/forName "Capsule")))
    (if (and (= base-type :thin) (not (seq excepts)))
      (extract-jar-contents-to-capsule jar-files capsule)
      (copy-jar-files-into-capsule jar-files capsule))
    (.close capsule)))

(defn- build-thin-spec [jar-files project spec]
  "Builds a thin capsule"
  (build-mixed :thin jar-files project spec))

(defn- build-fat-spec [jar-files project spec]
  "Builds a fat capsule"
  (build-mixed :fat jar-files project spec))

(defn- build-thin-except-clojure-spec [jar-files project spec]
  "Builds a thin capsule embedding clojure"
  (build-mixed :thin jar-files project spec '[[org.clojure/clojure]]))

(defn- build-fat-except-clojure-spec [jar-files project spec]
  "Builds a fat capsule excluding clojure"
  (build-mixed :fat jar-files project spec '[[org.clojure/clojure]]))

; TODO Case for multi?
(defn- build-mixed-spec [jar-files project spec]
  "Builds a mixed capsule"
  (cond
    (:fat-except spec) (build-mixed :fat jar-files project spec (:fat-except spec))
    (:thin-except spec) (build-mixed :thin jar-files project spec (:thin-except spec))
    :else
      (throw (RuntimeException. "Unexpected mixed capsule type specified, use either :fat-except or :thin-except"))))

; TODO Case for multi?
(defn ^:internal build-capsule [jar-files project capsule-type-name capsule-type-spec]
  "Builds the capsule(s) of a given type using the pre-processed project map"
  ((case capsule-type-name
     :thin build-thin-spec
     :fat build-fat-spec
     :thin-except-clojure build-thin-except-clojure-spec
     :fat-except-clojure build-fat-except-clojure-spec
     :mixed build-mixed-spec)
    jar-files project capsule-type-spec)
  (main/info (str "Created " (name capsule-type-name) " capsule "
                  (get-capsules-output-dir project) "/" (:name capsule-type-spec) "")))