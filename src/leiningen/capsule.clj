; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns leiningen.capsule
  "Creates the specified capsules for the project."

  (:require
    [clojure.string :as cstr]

    [leiningen.capsule.aether :as aether-ported]

    [leiningen.core.main :as main]
    [leiningen.core.project :as project]

    [leiningen.compile :as compile]
    [leiningen.clean :as clean]
    [leiningen.pprint :as pprint]

    [leiningen.capsule-utils :as cutils]
    [leiningen.capsule-consts :as cc]

    [cemerick.pomegranate.aether :as aether])

  (:import
    (co.paralleluniverse.capsule.build Dependencies)
    (org.eclipse.aether.repository RemoteRepository)
    (org.eclipse.aether.graph Dependency)
    (co.paralleluniverse.capsule Jar)))

(defn- build-thin [spec]
  ; TODO Implement
)

(defn- build-fat [spec]
  ; TODO Implement
)

(defn- build-mixed [spec & [capsule-type-name]]
  ; TODO Implement
)

(defn- build-capsules [builder specs & more]
  (let [specs (cond (map? specs) [specs] (coll? specs) specs :else [])]
    (doseq [spec specs]
      (apply builder (conj spec more)))))

(defn- build-capsule [project capsule-type-name]
  "Builds the capsule(s) of a given type using the pre-processed project map"
  (cond
    (= capsule-type-name :thin)
      (build-capsules build-thin (get-in project (conj cc/path-types :thin))))
    (= capsule-type-name :fat)
      (build-capsules build-fat (get-in project (conj cc/path-types :fat)))
    (contains? capsule-type-name #{:mixed :fat-except-clojure})
      (build-capsules build-mixed (get-in project (conj cc/path-types :mixed)) capsule-type-name))

(defn- add-to-manifest-if-path [project path manifest-entry-name f & [profile-keyword]]
  "Will add an entry's transformation through \"f\" in the given non-profile-aware project map path (if found) to the
  manifest map under the given profile-aware name"
  (let [value (get-in project path)
        transformed-value (f value)]
    (if (and value transformed-value)
      (update-in project (cc/capsule-manifest-path project profile-keyword)
        #(merge
          (or % {})
          { manifest-entry-name transformed-value }))
      project)))

(defn- add-to-manifest-if-profile-path [project path manifest-entry-name f & [profile-keyword]]
  "Will add an entry's transformation through \"f\" in the given profile-aware project map path (if found) to the
  manifest map under the given profile-aware name"
  (add-to-manifest-if-path
    project
    (cc/profile-aware-path project path profile-keyword)
    manifest-entry-name
    f
    profile-keyword))

(defn- add-to-manifest-if-profile-path-as-string [project path manifest-entry-name & [profile-keyword]]
  "Will add an entry's toString() in the given profile-aware project map path (if found) to the manifest map under
  the given profile-aware name"
  (add-to-manifest-if-profile-path
    project
    path manifest-entry-name
    #(if % (let [val (.toString %)] (if (seq val) val nil)) nil)
    profile-keyword))

(defn- add-to-manifest [project manifest-entry-name manifest-entry-value & [profile-keyword]]
  "Will add the specified entry to the manifest map"
  (if (seq manifest-entry-value)
    (update-in project
               (cc/capsule-manifest-path project profile-keyword)
               #(merge % { manifest-entry-name manifest-entry-value }))
    project))

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
        (add-to-manifest "Application-Class" "clojure.main" profile-keyword)
        (add-to-manifest "Args" (.trim (str main-ns " " args)) profile-keyword))
      (let [project
             (if (> (.length args) 0)
               (add-to-manifest project "Args" args profile-keyword)
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
              (add-to-manifest "Unix-Script" (:unix scripts) profile-keyword)
              (add-to-manifest "Windows-Script" (:windows scripts) profile-keyword))
          artifact
            (->
              project
              (add-to-manifest
                "Application"
                (cutils/artifact-to-string artifact) profile-keyword))
          :else
            (->
              project
              (add-to-manifest
                "Application"
                (cutils/artifact-to-string artifact) profile-keyword))))))) ; This should never happen

(defn- manifest-put-runtime [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's runtime spec section"
  (->
    project
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-java-version "Java-Version" profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-min-java-version "Min-Java-Version" profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-min-update-version "Min-Update-Version" profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-jdk-required "JDK-Required" profile-keyword)
    (add-to-manifest-if-profile-path cc/path-runtime-jvm-args "JVM-Args" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path cc/path-runtime-system-properties "Environment-Variables"
      #(reduce-kv (fn [accum k v] (str accum " " k "=" v)) "" %) profile-keyword)
    (add-to-manifest-if-profile-path cc/path-runtime-agents "Java-Agents"
      #(reduce
        (fn [accum [k v]]
          (str accum " "
            (case k
              :embedded (str (:jar v) "=" (:params v))
              :artifact (str (cutils/artifact-to-string (:id v)) "=" (:params v))
              :else "")))
        "" %)
      profile-keyword)
    (add-to-manifest-if-profile-path
      cc/path-runtime-app-class-path "App-Class-Path" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path
      cc/path-runtime-boot-class-path-p "Boot-Class-Path-P" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path
      cc/path-runtime-boot-class-path-a "Boot-Class-Path-P" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path
      cc/path-runtime-boot-class-path "Boot-Class-Path" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path
      cc/path-runtime-native-library-path-p "Library-Path-P" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path
      cc/path-runtime-native-library-path-a "Library-Path-A" #(cstr/join " " %) profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-security-manager "Security-Manager" profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-security-policy-a "Security-Policy-A" profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-runtime-security-policy "Security-Policy" profile-keyword)))

(defn- manifest-put-boot [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's boot spec section"
  (let [project
         (if (not profile-keyword)
           (add-to-manifest project "Main-Class" (get-in project cc/path-execution-boot-main-class "Capsule"))
           project)]
    (->
      project
      (add-to-manifest-if-profile-path-as-string
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
    (add-to-manifest-if-profile-path-as-string
      cc/path-application-name profile-keyword "Application-Name" profile-keyword)
    (add-to-manifest-if-profile-path-as-string cc/path-application-version "Application-Version" profile-keyword)))

(defn- manifest-put-toplevel [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's top-level spec parts"
  (->
    project
    ; TODO Implement plugin version check
    (add-to-manifest-if-profile-path-as-string cc/path-log-level "Log-Level" profile-keyword)))

(declare capsulize)

(defn- manifest-put-profiles [project]
  "Recursively calls capsulization with profile names"
  (reduce-kv
    (fn [project k v] (capsulize project k))
    project
    (get-in project cc/path-profiles)))

(defn- manifest-put-mvn-repos [project & [profile-keyword]]
  (add-to-manifest
    project
    "Repositories"
    (cstr/join
      ","
      (map
        (fn [lein-repo]
          ; TODO Remove trick once https://github.com/cemerick/pomegranate/pull/69 is merged
          (println lein-repo)
          (Dependencies/toCapsuleRepositoryString (@#'aether-ported/make-repository lein-repo nil)))
        (:repositories project)))
    profile-keyword))

(defn- make-dep [lein-dep]
  ; TODO Remove trick once https://github.com/cemerick/pomegranate/pull/69 is merged
  (Dependencies/toCapsuleDependencyString (@#'aether-ported/dependency lein-dep)))

(defn- manifest-put-mvn-deps [project & [profile-keyword]]
  (let [make-dep #(Dependencies/toCapsuleDependencyString (@#'aether-ported/dependency %))
        jvm-lein (map make-dep (:dependencies project))
        jvm-removed (map make-dep (get-in project cc/path-maven-dependencies-artifacts-jvm-remove))
        jvm-added (map make-dep (get-in project cc/path-maven-dependencies-artifacts-jvm-add))
        jvm (cstr/join "," (concat jvm-added (seq (clojure.set/difference (set jvm-lein) (set jvm-removed)))))
        native-mac (cstr/join "," (map make-dep (get-in project cc/path-maven-dependencies-artifacts-native-mac)))
        native-linux (cstr/join "," (map make-dep (get-in project cc/path-maven-dependencies-artifacts-native-linux)))
        native-win (cstr/join "," (map make-dep (get-in project cc/path-maven-dependencies-artifacts-native-windows)))]
    (-> project
        (add-to-manifest "Dependencies" jvm profile-keyword)
        (add-to-manifest "Native-Dependencies-mac" native-mac profile-keyword)
        (add-to-manifest "Native-Dependencies-Linux" native-linux profile-keyword)
        (add-to-manifest "Native-Dependencies-Win" native-win profile-keyword))))

(defn- manifest-put-maven [project & [profile-keyword]]
  "Adds manifest entries implementing lein-capsule's deps spec section"
  (->
    project
    (add-to-manifest-if-profile-path-as-string
      cc/path-maven-dependencies-allow-snapshots "Allow-Snapshots" profile-keyword)
    (update-in cc/path-maven-dependencies-repositories #(cons cc/clojars-repo-url %))
    (manifest-put-mvn-repos profile-keyword)
    (manifest-put-mvn-deps profile-keyword)))

(defn- capsulize [project & [profile-keyword]]
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
  (or (get-in project cc/path-capsule-default-name) (str (:name project) "-capsule")))

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
(defn- validate-and-normalize
  "Validates and normalizes a project"
  [project]
  (normalize-capsule-spec project))

(defn capsule
  "Creates a capsule for the project"
  [project & args]
  (let [default-profile (cutils/get-capsule-default-profile project)
        project (validate-and-normalize project)]
    (reduce-kv
      (fn [_ capsule-type-name v]
        (let [project (cutils/get-project-without-default-profile project)
              ; TODO Check: should previous profile should be unmerged?
              project (project/merge-profiles project [{:capsule default-profile} {:capsule v}])
              project (capsulize project)]
          (clean/clean project) ; TODO Improve: avoid cleaning if/when possible
          (apply compile/compile (cons project args))
          (build-capsule project capsule-type-name)))
      nil (cutils/get-capsule-types project))))