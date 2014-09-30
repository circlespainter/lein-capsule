(ns leiningen.capsule-consts)

; Unused for now
; (def type-names [:thin :fat])

(def kwd-capsule-manifest :manifest)

(def path-main [:main])

(def path-log-level [:log-level])
(def path-application-name [:application :name])
(def path-application-version [:application :version])
(def path-capsule-default-name [:name])
(def path-execution-boot-main-class [:execution :boot :main-class])
(def path-execution-boot-clojure-ns [:execution :boot :clojure-ns])
(def path-execution-boot-scriptsx [:execution :boot :scripts])
(def path-execution-boot-artifact [:execution :boot :artifact])
(def path-execution-boot-extract-capsule [:execution :boot :extract-capsule])
(def path-execution-boot-args [:execution :boot :args])
(def path-execution-runtime [:execution :runtime])
(def path-runtime-java-version [:runtime :java-version])
(def path-runtime-min-java-version [:runtime :min-java-version])
(def path-runtime-min-update-version [:runtime :min-update-version])
(def path-runtime-jdk-required [:runtime :jdk-required])
(def path-runtime-jvm-args [:runtime :jvm-args])
(def path-runtime-system-properties [:runtime :system-properties])
(def path-runtime-agents [:runtime :agents])
(def path-runtime-app-class-path [:runtime :paths :app-class-path])
(def path-runtime-boot-class-path-p [:runtime :paths :boot-class-path :prepend])
(def path-runtime-boot-class-path-a [:runtime :paths :boot-class-path :append])
(def path-runtime-boot-class-path [:runtime :paths :boot-class-path :override])
(def path-runtime-native-library-path-p [:runtime :paths :native-library-path :prepend])
(def path-runtime-native-library-path-a [:runtime :paths :native-library-path :append])
(def path-runtime-security-manager [:runtime :security :manager])
(def path-runtime-security-policy-a [:runtime :security :policy :append])
(def path-runtime-security-policy [:runtime :security :policy :override])

(def path-profiles [:capsule :profiles])
(def path-types [:capsule :types])

(defn profile-aware-path [path & [profile-keyword]]
  (if profile-keyword
    (concat [:capsule :profiles profile-keyword] path)
    (cons :capsule path)))

(defn capsule-manifest-path [& [profile-keyword]]
	(if profile-keyword
		[kwd-capsule-manifest profile-keyword]
		[kwd-capsule-manifest]))