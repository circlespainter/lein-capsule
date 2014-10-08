; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns ^{ :author "circlespainter" :internal true } leiningen.capsule.spec
  "Normalizes then and pre-processes the capsule building specification into a prototypical manifest"

  (:require
    [clojure.string :as cstr]

    [leiningen.capsule.aether :as aether-ported]

    [leiningen.core.main :as main]
    [leiningen.core.project :as project]

    [leiningen.capsule.utils :as cutils]
    [leiningen.capsule.consts :as cc]
    [leiningen.capsule.build :as cbuild])

  (:import
    (org.eclipse.aether.repository RemoteRepository)
    (co.paralleluniverse.capsule.build Dependencies)))

(defn- setup-boot [project & [profile-keyword]]
  "Adds manifest entries for the executable jar's entry point"
  (let [main-ns
        (get-in project (cc/profile-aware-path project cc/path-execution-boot-clojure-ns profile-keyword)
                (get-in project cc/path-main))
        args
        (.trim
          (reduce
            (fn [accum v]
              (str accum " " v))
            ""
            (get-in project cc/path-execution-boot-args [])))]
    (if main-ns
      (->
        project
        (cutils/add-to-manifest "Application-Class" "clojure.main" profile-keyword)
        (cutils/add-to-manifest "Args" (.trim (str main-ns " " args)) profile-keyword))
      (let [project
            (if (> (.length args) 0)
              (cutils/add-to-manifest project "Args" args profile-keyword)
              project)
            scripts
            (get-in project (cc/profile-aware-path project cc/path-execution-boot-scriptsx profile-keyword))
            artifact ; Default if unspecified is project artifact
            (get-in project (cc/profile-aware-path project cc/path-execution-boot-artifact profile-keyword)
                    [(symbol (:group project) (:name project)) (:version project)])]
        (cond
          scripts
          (->
            project
            (cutils/add-to-manifest "Unix-Script" (:unix scripts) profile-keyword)
            (cutils/add-to-manifest "Windows-Script" (:windows scripts) profile-keyword))
          artifact
          (->
            project
            (cutils/add-to-manifest
              "Application"
              (cutils/artifact-to-string artifact) profile-keyword))
          :else
          (->
            project
            (cutils/add-to-manifest
              "Application"
              (cutils/artifact-to-string artifact) profile-keyword))))))) ; This should never happen

(defn- manifest-put-runtime [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's runtime spec section"
  (->
    project
    (cutils/add-to-manifest-if-profile-path-as-string cc/path-runtime-java-version "Java-Version" profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string cc/path-runtime-min-java-version "Min-Java-Version"
                                                      profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string cc/path-runtime-min-update-version "Min-Update-Version"
                                                      profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string cc/path-runtime-jdk-required "JDK-Required" profile-keyword)
    (cutils/add-to-manifest-if-profile-path cc/path-runtime-jvm-args "JVM-Args" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path cc/path-runtime-system-properties "Environment-Variables"
                                            #(reduce-kv (fn [accum k v] (str accum " " k "=" v)) "" %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path cc/path-runtime-agents "Java-Agents"
                                            #(reduce
                                              (fn [accum [k v]]
                                                (str accum " "
                                                     (case k
                                                       :embedded (str (:jar v) "=" (:params v))
                                                       :artifact
                                                       (str (cutils/artifact-to-string (:id v)) "=" (:params v))
                                                       :else "")))
                                              "" %)
                                            profile-keyword)
    (cutils/add-to-manifest-if-profile-path
      cc/path-runtime-app-class-path "App-Class-Path" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path
      cc/path-runtime-boot-class-path-p "Boot-Class-Path-P" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path
      cc/path-runtime-boot-class-path-a "Boot-Class-Path-P" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path
      cc/path-runtime-boot-class-path "Boot-Class-Path" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path
      cc/path-runtime-native-library-path-p "Library-Path-P" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path
      cc/path-runtime-native-library-path-a "Library-Path-A" #(cstr/join " " %) profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string
      cc/path-runtime-security-manager "Security-Manager" profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string
      cc/path-runtime-security-policy-a "Security-Policy-A" profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string
      cc/path-runtime-security-policy "Security-Policy" profile-keyword)))

(defn- manifest-put-boot [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's boot spec section"
  (let [project
        (if (not profile-keyword)
          (cutils/add-to-manifest project "Main-Class" (get-in project cc/path-execution-boot-main-class "Capsule"))
          project)]
    (->
      project
      (cutils/add-to-manifest-if-profile-path-as-string
        cc/path-execution-boot-extract-capsule "Extract-Capsule" profile-keyword)
      (setup-boot profile-keyword))))

(defn- manifest-put-execution [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's execution spec section"
  (->
    project
    (manifest-put-boot profile-keyword)
    (manifest-put-runtime profile-keyword)))

(defn- manifest-put-application [project & [profile-keyword]]
  "Adds capsule's application name and version manifest entries"
  (->
    project
    (cutils/add-to-manifest-if-profile-path-as-string
      cc/path-application-name profile-keyword "Application-Name" profile-keyword)
    (cutils/add-to-manifest-if-profile-path-as-string cc/path-application-version "Application-Version"
                                                      profile-keyword)))

(defn- manifest-put-toplevel [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's top-level spec parts"
  (->
    project
    ; TODO Implement plugin version check
    (cutils/add-to-manifest-if-profile-path-as-string cc/path-log-level "Log-Level" profile-keyword)))

(declare capsulize)

(defn- manifest-put-profiles [project]
  "Recursively calls capsulization with profile names"
  (reduce-kv
    (fn [project k _] (capsulize project k))
    project
    (get-in project cc/path-profiles)))

(defn- manifest-put-maven [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's deps spec section"
  (->
    project
    (cutils/add-to-manifest-if-profile-path-as-string
      cc/path-maven-dependencies-allow-snapshots "Allow-Snapshots" profile-keyword)
    (update-in cc/path-maven-dependencies-repositories #(cons cc/clojars-repo-url %))))

(defn ^:internal capsulize [project & [profile-keyword]]
  "Augments the manifest inserting capsule-related entries"
  (let [user-manifest (if profile-keyword {} (or (:manifest project) {}))   ; backup existing user manifest
        maybe-manifest-put-profiles (if profile-keyword identity manifest-put-profiles)
        project
        (->
          project
          (manifest-put-toplevel profile-keyword)
          (manifest-put-application profile-keyword)
          (manifest-put-execution profile-keyword)
          (manifest-put-maven profile-keyword)
          (maybe-manifest-put-profiles) ; Needs to be the last step as the default profile can override anything
          (update-in (cc/capsule-manifest-path project profile-keyword)
                     ; priority to user manifest
                     #(merge % user-manifest)))]
    (if profile-keyword
      project
      (project/merge-profiles project [{cc/kwd-capsule-manifest (cutils/capsule-manifest project)}]))))

; TODO Improve error reporting
; TODO Validate Scripts
; TODO Validate Extract-Capsule
(defn- validate-execution [project]
  "Validates specification of execution boot settings"
  (let [agents (:agents (get-in project cc/path-execution-runtime))]
    (doseq [a agents]
      (if (not (or (get-in a [:embedded :jar]) (get-in a [:artifact :id])))
        (do
          (main/warn "FATAL: some agents miss coordinates")
          (main/exit))))
    project))

(defn- default-capsule-name [project]
  "Extracts or build the default capsule name"
  (str (or (get-in project cc/path-capsule-default-name) (:name project)) "-" (:version project) "-capsule.jar"))

; TODO Improve error reporting
(defn- normalize-types [project]
  "Validates (and makes more handy for subsequent build steps) the specification of capsules to be built"
  (let [types (get-in project cc/path-types)
        capsule-names
        (filter
          identity
          (flatten
            (map
              #(cond
                (map? %) (:name %)
                (coll? %) (map (fn [elem] (:name elem)) %)
                :else nil)
              (vals types))))
        capsule-names-count (count capsule-names)]
    (cond
      (>
        (apply +
               (map
                 #(cond
                   (map? %) 1
                   (coll? %) (count %)
                   :else 0)
                 (vals types)))
        (+ 1 capsule-names-count)) ; At most one type can avoid specifying capsule name
      (do
        (main/warn "FATAL: all capsule types must define capsule names (except one at most), exiting")
        (main/exit))
      (not= capsule-names-count (count (distinct capsule-names))) ; Names must be non-conflicting
      (do
        (main/warn "FATAL: conflicting capsule names found: " capsule-names)
        (main/exit))
      :else
      (update-in project cc/path-types
                 #(let [set-explicit-name
                        (fn [type-val] (merge type-val {:name (or
                                                                (:name type-val)
                                                                (default-capsule-name project))}))
                        new-project ; make type name explicit
                        (reduce-kv
                          (fn [types type-key type-val]
                            (merge types
                                   { type-key
                                     (cond
                                       (map? type-val)
                                       (set-explicit-name type-val)
                                       (coll? type-val)
                                       (map set-explicit-name type-val)
                                       :else type-val) } ))
                          % %)]
                   new-project)))))

; TODO Improve error reporting
(defn- validate-capsule-profiles [project]
  "Validates all defined capsule profiles"
  (let [modes (get-in project cc/path-profiles)
        default-profiles-count (count (filter nil? (map #(:default %) (vals modes))))]
    (cond
      (> default-profiles-count 1)
      (do
        (main/warn "FATAL: at most one mode can be marked as default")
        (main/exit))
      :else
      project)))

(defn- normalize-capsule-spec [project]
  "Performs full validation of the project capsule specification"
  (->
    project
    normalize-types
    validate-capsule-profiles
    validate-execution))

; TODO Validate leaf types
(defn ^:internal validate-and-normalize
  "Validates and normalizes a project"
  [project]
  (normalize-capsule-spec project))