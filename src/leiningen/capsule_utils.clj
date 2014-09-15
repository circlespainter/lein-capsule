(ns leiningen.capsule-utils
	(:require [leiningen.pprint :as pprint]
						[leiningen.capsule-consts :as cc]))

; TODO Comment out once working
(defn pprint-and-return [project]
	"Pretty-print and returns a project, convenient for use with ->"
	(pprint/pprint project)
	project)

(defn get-capsule-types [project]
	"Extracts and returns the enabled capsule types sub-map, if any"
	(get-in project cc/path-types))

(defn get-capsule-default-profile [project]
	"Extracts and returns the default capsule profile sub-map, if any"
	(reduce-kv (fn [i k v] (merge i (if (:default v) (dissoc v :default) {}))) {} (get-in project cc/path-profiles)))

(defn get-capsule-default-profile-name [project]
	"Extracts and returns the default capsule profile sub-map, if any"
	(reduce-kv (fn [i k v] (str i (if (:default v) k ""))) "" (get-in project cc/path-profiles)))

(defn get-project-without-default-profile [project]
	(update-in project cc/path-profiles
		#(reduce-kv (fn [profiles-map k v] (if (:default v) (dissoc profiles-map k) profiles-map)) %1 %1)))

(defn artifact-to-string [[sym ver]]
	(let [nmspc (namespace sym)
				nmsym (name sym)]
		(str
			(if nmspc (str nmspc ":") "")
			nmsym ":" ver)))