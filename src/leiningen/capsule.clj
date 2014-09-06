(ns leiningen.capsule
	"Creates a capsule for the project."
	(:require [leiningen.core.main :as main]
						[leiningen.core.project :as project]
						[leiningen.compile :as compile]
						[leiningen.clean :as clean]))

; Unused for now
; (def ^:private type-names [:thin :fat])

(def ^:private application-name-path [:capsule :application :name])
(def ^:private application-version-path [:capsule :application :version])
(def ^:private capsule-default-name-path [:capsule :name])
(def ^:private execution-runtime-path [:capsule :execution :runtime])
(def ^:private log-level-path [:capsule :log-level])
(def ^:private profiles-path [:capsule :profiles])
(def ^:private types-path [:capsule :types])

(defn- same [x] x)

(defn- add-to-manifest-if-path [project path key f]
	(let [value (get-in project path)]
		(if value (update-in project [:manifest] #(merge %1 {key (f value)})) project)))

(defn- manifest-put-application [project]
	(->
		project
		(add-to-manifest-if-path application-name-path "Application-Name" same)
		(add-to-manifest-if-path application-version-path "Application-Version" same)))

(defn- manifest-put-toplevel [project]
	(->
		project
		; TODO Implement plugin version check
		(add-to-manifest-if-path log-level-path "Log-Level" same)))

(defn- capsulize [project capsule-type]
	"Augments the manifest inserting capsule-related entries"
	(let [user-manifest (or (:manifest project) {})   ; backup existing user manifest
				project (update-in [:manifest] project [:manifest] {})] ; reset manifest
		(->
			project
			(manifest-put-toplevel)
			(manifest-put-application)
			; TODO Implement
			; (manifest-put-modes capsule-type)
			; (manifest-put-execution capsule-type)
			; (manifest-put-deps capsule-type) ; TODO Remember to always add clojars as additional default
			(update-in [:manifest] #(merge %1 user-manifest)) ; priority to user manifest
		)))

(defn- build-capsule [project capsule-type]
	"Builds the capsule of given type using the pre-processed project map"
	(let [project (capsulize project capsule-type)]
		(main/info "Unimplemented (yet)"))) ; TODO Implement

(defn- merge-overrides [project capsule-type-map]
	"Merges as a profile the given capsule type sub-map"
	(->
		(update-in project [:profiles] merge {:current-capsule-profile capsule-type-map})
		(project/merge-profiles [:current-capsule-profile])))

(defn- get-capsule-types [project]
	"Extracts and returns the enabled capsule types sub-map, if any"
	(get-in project types-path))

; TODO Improve error reporting
(defn- validate-execution-runtime-agents [project]
	"Validates specification of execution boot settings"
	(let [agents (:agents (get-in project execution-runtime-path))]
		(doseq [a agents]
			(if (not (or (get-in a [:embedded :jar]) (get-in a [:artifact :id])))
				(do
					(main/warn "FATAL: some agents miss coordinates")
					(comment main/exit))))
		project))

(defn- default-capsule-name [project]
	"Extracts or build the default capsule name"
	(or (get-in project capsule-default-name-path) (str (:name project) "-capsule")))

; TODO Improve error reporting
(defn- normalize-profiles [project]
	(let [modes (get-in project profiles-path)
				default-profiles-count (count (map #(:default %1) (vals modes)))]
		(cond
			(> 1 default-profiles-count)
				(do
					(main/warn "FATAL: at most one mode can be marked as default")
					(main/exit))
			(= 1 default-profiles-count) ; Merge mode as profile in :capsule if it's default
				(let
					[default-profile-pair
						(flatten (map
							#(if (:default (second %1)) %1)
							(seq modes)))
					 new-capsule (project/merge-profiles (:capsule project) [(first default-profile-pair)])]
					(update-in project [:capsule] new-capsule))
			:else
				project)))

; TODO Improve error reporting
(defn- normalize-types [project]
	"Validates (and makes more handy for subsequent build steps) the specification of capsules to be built"
	(let [types (get-in project types-path)
				capsule-names (flatten (map #(:name %1) (vals types)))
				capsule-names-count (count capsule-names)]
		(cond
			(> (count (keys types)) (+ 1 capsule-names-count))
				(do
					(main/warn "FATAL: all capsule types must define capsule names (except one at most), exiting")
					(main/exit))
			(not= capsule-names-count (count (distinct capsule-names)))
				(do
					(main/warn "FATAL: conflicting capsule names found: " capsule-names)
					(main/exit))
			:else
				(update-in project types-path
					(reduce-kv (fn [types type-key type-map]
						(merge types
							{ type-key
								(or
									(:name type-map)
									(default-capsule-name project)) }))
						types types)))))

(defn- normalize-capsule-spec [project]
	"Performs full validation of the project capsule specification"
	(->
		project
		normalize-types
		normalize-profiles
		validate-execution-runtime-agents))

(defn capsule
	"Creates a capsule for the project"
	[project & args]
	(let [project (normalize-capsule-spec project)]
		(doseq [capsule-type-map (get-capsule-types project)]
			(let [project (merge-overrides project capsule-type-map)
						capsule-type-name (first (keys capsule-type-map))]
				(main/info "CAPSULE: processing spec '" +  + "'")
				(clean/clean project) ; TODO Improve: avoid cleaning if/when possible
				(apply compile/compile (cons project args))
				; Middleware will have already filled leiningen's manifest map appropriately
				(build-capsule project capsule-type-name)))))