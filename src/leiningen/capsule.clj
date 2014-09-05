(ns leiningen.capsule
	"Creates a capsule for the project."
	(:require [leiningen.core.main :as main]
						[leiningen.core.project :as project]
						[leiningen.compile :as compile]
						[leiningen.clean :as clean]))

; Unused for now
; (def ^:private type-names [:thin :fat])

(def ^:private capsule-default-name-path [:capsule :name])
(def ^:private types-path [:capsule :types])
(def ^:private execution-runtime-path [:capsule :execution :runtime])

; (defn- mf-put-toplevel [project capsule-type])

(defn- capsulize [project capsule-type]
	"Augments the manifest inserting capsule-related entries"
	(->
		project
		; TODO Implement
		; (mf-put-toplevel capsule-type)
		; (mf-put-application capsule-type)
		; (mf-put-modes capsule-type)
		; (mf-put-execution capsule-type)
		; (mf-put-deps capsule-type) ; TODO Remember to always add clojars as additional default
	))

(defn- build-capsule [project capsule-type]
	"Builds the capsule of given type using the pre-processed project map"
	(let [project (capsulize project capsule-type)]
		(main/info "Unimplemented (yet)"))) ; TODO Implement

(defn- merge-overrides [project capsule-type-profile-map]
	"Merges as a profile the given capsule type sub-map"
	(->
		(update-in project [:profiles] merge capsule-type-profile-map)
		(project/merge-profiles project [(first (keys capsule-type-profile-map))])))

(defn- get-enabled-capsules [project]
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
		validate-execution-runtime-agents))

(defn capsule
	"Creates a capsule for the project"
	[project & args]
	(let [project (normalize-capsule-spec project)]
		(doseq [capsule-type-profile-map (get-enabled-capsules project)]
			(let [project (merge-overrides project capsule-type-profile-map)
						capsule-type-name (first (keys capsule-type-profile-map))]
				(main/info "CAPSULE: processing spec '" +  + "'")
				(clean/clean project) ; TODO Improve: avoid cleaning if/when possible
				(apply compile/compile (cons project args))
				; Middleware will have already filled leiningen's manifest map appropriately
				(build-capsule project capsule-type-name)))))