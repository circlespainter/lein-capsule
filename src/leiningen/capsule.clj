(ns leiningen.capsule
		"Creates a capsule for the project."
		(:require [clojure.xml :as xml]
							[clojure.zip :as zip]
							[clojure.java.io :as io]
							[leiningen.core.classpath :as classpath]
							[leiningen.core.project :as project]
							[leiningen.core.main :as main]
							[leiningen.core.utils :as utils]
							[leiningen.compile :as compile]
							[leiningen.jar :as jar])
		(:import (java.io File FileOutputStream PrintWriter)
						 (java.util.regex Pattern)
						 (java.util.zip ZipFile ZipOutputStream ZipEntry)
						 (org.apache.commons.io.output CloseShieldOutputStream)))

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
			mf-put-log)))

(defn- fill-capsule-dependency [project]
	(update-in project [:dependencies]
		(fn [deps]
			project))) ; TODO Implement

(defn- capsulize [project]
	(->
		project
		fill-capsule-dependency
		fill-capsule-manifest))

(defn- build-capsule [project]
	(let [
		project (project/merge-profiles project [:capsule])
		project (capsulize project)
	 ]
		(main/info "Unimplemented (yet)"))) ; TODO Implement

(defn capsule
	"Creates a capsule for the project."
	[project & args]
	 (apply compile/compile (cons project args))
	 ; Middleware will have already filled leiningen's manifest map appropriately
	 (apply build-capsule [project]))