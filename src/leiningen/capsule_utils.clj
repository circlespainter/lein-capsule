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
	(get-in project cc/types-path))

(defn get-capsule-default-profile [project]
	"Extracts and returns the default capsule profile sub-map, if any"
	(reduce-kv (fn [i k v] (merge i (if (:default v) (dissoc v :default) {}))) {} (get-in project cc/profiles-path)))

(defn get-project-without-default-profile [project] project) ; TODO Implement