(ns capsule.plugin)



(defn- fill-capsule-manifest [project]
	(->
		project
		(comment	; TODO Implement all
			mf-put-execution
			mf-put-application-id
			mf-put-runtime
			mf-put-cache-settings
			mf-put-maven
			mf-put-classpaths
			mf-put-jvm
			mf-put-security
			mf-put-log
		)))

(defn- fill-capsule-dependency [project]
	project) ; TODO Implement

(defn middleware [project]
	(->
		project
		fill-capsule-dependency
		fill-capsule-manifest))