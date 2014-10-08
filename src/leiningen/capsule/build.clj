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
    (org.eclipse.aether.graph Dependency)
    (co.paralleluniverse.capsule Jar)
    (java.io File FileOutputStream)))

(defn- make-aether-dep [lein-style-spec]
  "Builds an Aether dependency from a Leinigen one"
  (Dependencies/toCapsuleDependencyString (@#'aether-ported/dependency lein-style-spec)))

(defn- jvm-lein-deps [project]
  "Extracts Leiningen dependencies from a project"
  (:dependencies project))

(defn- capsule-deps [project path & [profile-keyword]]
  "Extracts Capsule-spec dependencies from a specific path location"
  (get-in project (cc/profile-aware-path project path profile-keyword)))

(defn- jvm-capsule-removed-deps [project & [profile-keyword]]
  "Extracts Capsule-spec removed dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-jvm-remove profile-keyword))

(defn- jvm-capsule-added-deps [project & [profile-keyword]]
  "Extracts Capsule-spec added dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-jvm-add profile-keyword))

(defn- native-mac-capsule-deps [project & [profile-keyword]]
  "Extracts Capsule-spec Mac native dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-native-mac profile-keyword))

(defn- native-linux-capsule-deps [project & [profile-keyword]]
  "Extracts Capsule-spec Linux native dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-native-linux profile-keyword))

(defn- native-windows-capsule-deps [project & [profile-keyword]]
  "Extracts Capsule-spec Windows native dependencies"
  (capsule-deps project cc/path-maven-dependencies-artifacts-native-windows profile-keyword))

(defn- jvm-computed-capsule-deps [project & [profile-keyword]]
  "Computes JVM dependencies based on both Leiningen and Capsule-spec"
  (concat
    (jvm-capsule-added-deps project profile-keyword)
    (vec
      (clojure.set/difference
        (set (jvm-lein-deps project))
        (set (jvm-capsule-removed-deps project profile-keyword))))))

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
    (and (= exceptions-mode :only) (not (seq exceptions))) []
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

(defn- manifest-put-mvn-repos [project & [profile-keyword]]
  "Adds repositories to the Capsule manifest"
  (cutils/add-to-manifest
    project
    "Repositories"
    (cstr/join
      ","
      (map
        make-aether-repo
        (lein-repos project)))
    profile-keyword))

(declare retrieve-and-insert-mvn-deps)

(defn- manifest-put-mvn-deps [project capsule & [profile-keyword exceptions exceptions-mode]]
  "Specifies some, all or all except some project dependencies for Capsule to retrieve at jar boot, inserting the
  remaining ones in the capsule jar under a Capsule-compliant tree structure"
  (let [jvm-deps (jvm-computed-capsule-deps project profile-keyword)
        jvm
          (cstr/join "," (map make-aether-dep
                              (filter-deps jvm-deps exceptions exceptions-mode)))
        native-mac
          (cstr/join "," (map make-aether-dep
                              (filter-deps (native-mac-capsule-deps project profile-keyword) exceptions exceptions-mode)))
        native-linux
          (cstr/join "," (map make-aether-dep
                              (filter-deps (native-linux-capsule-deps project profile-keyword) exceptions exceptions-mode)))
        native-win
          (cstr/join "," (map make-aether-dep
                              (filter-deps (native-windows-capsule-deps project profile-keyword) exceptions exceptions-mode)))]
    (-> project
        (cutils/add-to-manifest "Dependencies" jvm profile-keyword)
        (cutils/add-to-manifest "Native-Dependencies-mac" native-mac profile-keyword)
        (cutils/add-to-manifest "Native-Dependencies-Linux" native-linux profile-keyword)
        (cutils/add-to-manifest "Native-Dependencies-Win" native-win profile-keyword))))

(defn- retrieve-and-insert-mvn-deps [project capsule & [profile-keyword exceptions exceptions-mode]]
  "Retrieves some, all or all except some project dependencies and inserts them in the capsule jar under a
   Capsule-compliant tree structure, leaving the remaining ones for Capsule to retrieve at jar boot"
  (let [jvm (filter-deps (jvm-computed-capsule-deps project profile-keyword) exceptions exceptions-mode)
        native-mac (filter-deps (native-mac-capsule-deps project profile-keyword) exceptions exceptions-mode)
        native-linux (filter-deps (native-linux-capsule-deps project profile-keyword) exceptions exceptions-mode)
        native-win (filter-deps (native-windows-capsule-deps project profile-keyword) exceptions exceptions-mode)]
    (doseq [dep-file
              (aether/dependency-files
                (aether/resolve-dependencies
                  :coordinates (concat jvm native-mac native-linux native-win)
                  :repositories (lein-repos project)))]
      (.addEntry capsule (.getName dep-file) dep-file))))

(defn- write-manifest [capsule project]
  "Writes the Capsule manifest"
  (doseq [[k v] (get-in project (cc/capsule-manifest-path project))]
    (cond
      (string? v) (.setAttribute capsule k v)
      (coll? v)
      (doseq [[section-k section-v] v]
        (.setAttribute capsule (name k) section-k section-v)))))

(defn- get-capsules-output-dir [project]
  (str (:target-path project) "/" (or (.toString (get-in project (cons :capsule cc/path-output-dir))) "capsules")))

(defn- capsule-output-stream [project spec]
  "Builds a mixed capsule based on thin defaults"
  (let [out-file-name (str (get-capsules-output-dir project) "/" (:name spec))]
    (clojure.java.io/make-parents out-file-name)
    (FileOutputStream. out-file-name)))

; TODO Embed application build as jar or expanded, depending on cases

(defn- build-mixed [base-type project spec & [excepts]]
  "Builds a mixed capsule"
  ; Will add every dependency to the manifest
  (let [capsule (Jar.)
        _ (.setOutputStream capsule (capsule-output-stream project spec))
        project
          (reduce-kv
            (fn [project k _]
              (-> project
                  (manifest-put-mvn-repos k)
                  (manifest-put-mvn-deps capsule k excepts (case base-type :fat :only :thin :except))))
            project
            (get-in project cc/path-profiles))
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
            (get-in project cc/path-profiles))]
    (if (or (not= base-type :fat) (seq excepts))
      (.addPackageOf capsule (Class/forName "capsule.UserSettings")))
    (doto capsule
      (.addClass (Class/forName "Capsule"))
      .close)))

(defn- build-thin-spec [project spec]
  "Builds a thin capsule"
  (build-mixed :thin project spec))

(defn- build-fat-spec [project spec]
  "Builds a fat capsule"
  (build-mixed :fat project spec))

(defn- build-thin-except-clojure-spec [project spec]
  "Builds a thin capsule embedding clojure"
  (build-mixed :thin project spec '[[org.clojure/clojure]]))

(defn- build-fat-except-clojure-spec [project spec]
  "Builds a fat capsule excluding clojure"
  (build-mixed :fat project spec '[[org.clojure/clojure]]))

(defn- build-mixed-spec [project spec]
  "Builds a mixed capsule"
  (cond
    (:fat-except spec) (build-mixed :fat project spec (:fat-except spec))
    (:thin-except spec) (build-mixed :thin project spec (:thin-except spec))
    :else
      (throw (RuntimeException. "Unexpected mixed capsule type specified, use either :fat-except or :thin-except"))))

(defn ^:internal build-capsule [jar-files project capsule-type-name capsule-type-spec]
  "Builds the capsule(s) of a given type using the pre-processed project map"
  (case capsule-type-name
    :thin (build-thin-spec project capsule-type-spec)
    :fat (build-fat-spec project capsule-type-spec)
    :thin-except-clojure (build-thin-except-clojure-spec project capsule-type-spec)
    :fat-except-clojure (build-fat-except-clojure-spec project capsule-type-spec)
    :mixed (build-mixed-spec project capsule-type-spec))
  (main/info (str "Created " (name capsule-type-name) " capsule into "
                  (get-capsules-output-dir project) "/" (:name capsule-type-spec) "")))