(ns leiningen.capsule
	"Creates a capsule for the project."
	(:require [leiningen.core.main :as main]
						[leiningen.core.project :as project]

						[leiningen.compile :as compile]
						[leiningen.clean :as clean]
						[leiningen.pprint :as pprint]

						[leiningen.capsule-utils :as cutils]
						[leiningen.capsule-consts :as cc]))

(defn- build-capsule [project capsule-type]
	"Builds the capsule of given type using the pre-processed project map"
	(main/info "\nCAPSULE, capsule build unimplemented (yet)\n")) ; TODO Implement

(defn- add-to-manifest-if-path [project path key f]
	(let [value (get-in project path)]
		(if value (update-in project [:manifest] #(merge %1 {key (f value)})) project)))

(defn- manifest-put-application [project]
	(->
		project
		(add-to-manifest-if-path cc/application-name-path "Application-Name" identity)
		(add-to-manifest-if-path cc/application-version-path "Application-Version" identity)))

(defn- manifest-put-toplevel [project]
	(->
		project
		; TODO Implement plugin version check
		(add-to-manifest-if-path cc/log-level-path "Log-Level" identity)))

(defn- capsulize [project capsule-type]
	"Augments the manifest inserting capsule-related entries"
	(let [user-manifest (or (:manifest project) {})   ; backup existing user manifest
				project (update-in project [:manifest] (fn[_]{}))] ; reset manifest
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

; TODO Improve error reporting
(defn- validate-execution-runtime-agents [project]
	"Validates specification of execution boot settings"
	(let [agents (:agents (get-in project cc/execution-runtime-path))]
		(doseq [a agents]
			(if (not (or (get-in a [:embedded :jar]) (get-in a [:artifact :id])))
				(do
					(main/warn "FATAL: some agents miss coordinates")
					(main/exit))))
		project))

(defn- default-capsule-name [project]
	"Extracts or build the default capsule name"
	(or (get-in project cc/capsule-default-name-path) (str (:name project) "-capsule")))

; TODO Improve error reporting
(defn- normalize-types [project]
	"Validates (and makes more handy for subsequent build steps) the specification of capsules to be built"
	(let [types (get-in project cc/types-path)
				capsule-names (filter identity (map #(:name %1) (vals types)))
				; _ (main/info "\nCapsule names: " capsule-names) ; TODO Comment out/remove once working
				capsule-names-count (count capsule-names)]
		(cond
			(> (count (keys types)) (+ 1 capsule-names-count)) ; At most one type can avoid specifying capsule name
			(do
				(main/warn "FATAL: all capsule types must define capsule names (except one at most), exiting")
				(main/exit))
			(not= capsule-names-count (count (distinct capsule-names))) ; Names must be non-conflicting
			(do
				(main/warn "FATAL: conflicting capsule names found: " capsule-names)
				(main/exit))
			:else
			(update-in project cc/types-path
				#(let [new-project ; make type name explicit
								(reduce-kv (fn [types type-key type-map]
									(merge types
										{ type-key
											(merge type-map { :name (or
												(:name type-map)
												(default-capsule-name project)) } ) } )) %1 %1)]
									 ; (main/info "\nNew types: " res) ; TODO Comment out/remove once working
					new-project)))))

; TODO Improve error reporting
(defn- validate-capsule-profiles [project]
	(let [modes (get-in project cc/profiles-path)
				default-profiles-count (count (filter nil? (map #(:default %1) (vals modes))))]
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
		validate-execution-runtime-agents))

(defn- validate-and-normalize
	"Validates and normalizes a project"
	[project]
	(let [_ (do (main/info "\nCAPSULE (middleware), original:\n") (pprint/pprint project)) ; TODO Comment out/remove once working
				project (normalize-capsule-spec project)
				_ (do (main/info "\nCAPSULE (middleware), normalized:\n") (pprint/pprint project))]
		project)) ; TODO Comment out/remove once working

(defn capsule
	"Creates a capsule for the project"
	[project & args]
	(let [default-profile (cutils/get-capsule-default-profile project)
				_ (main/info "\nCAPSULE, default profile: " default-profile)
				project (validate-and-normalize project)]
		(reduce-kv
			(fn [_ capsule-type-name v]
				; (println "\nCapsule type map: " {k v}) ; TODO Comment out/remove once working
				(let [_ (do (main/info "\nCAPSULE, profile-merging '" v "':\n"))
							project (cutils/get-project-without-default-profile project)
							project (project/merge-profiles project [{:capsule default-profile} {:capsule v}])
							; TODO Comment out/remove once working
							_ (do (main/info "\nCAPSULE, profile merge for '" capsule-type-name "':\n") (pprint/pprint project))]
					(clean/clean project) ; TODO Improve: avoid cleaning if/when possible
					(apply compile/compile (cons project args))
					(build-capsule project capsule-type-name)))
			nil (cutils/get-capsule-types project))))