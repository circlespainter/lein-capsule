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

(defn- strip-capsule-args [a]
	(and
		(not= a :fat)
		(not= a :slim)))

(defn- build-capsule [project & args]
	(main/info "Unimplemented (yet)"))	; TODO Implement

(defn capsule
	"Creates a capsule for the project."
	([project & args]
	 (apply compile/compile (cons project (filter strip-capsule-args args)))
	 ; Middleware will have already filled leiningen's manifest map appropriately
	 (apply build-capsule (cons project args)))	; TODO Implement
	([project] (capsule project :fat))) ; Default is fat capsule