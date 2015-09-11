; lein-capsule: a Leiningen plugin for Parallel Universe's Capsule.
;
; Copyright (C) 2014 Fabio Tudone. All rights reserved.
;
; This program and the accompanying materials are dual-licensed under
; either the terms of the Eclipse Public License v1.0 as published by
; the Eclipse Foundation
;
;   or (per the licensee's choosing)
;
; under the terms of the GNU Lesser General Public License version 3.0
; as published by the Free Software Foundation.

; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns ^{ :author "circlespainter" :internal true } leiningen.capsule.utils
  "Various capsule-building utilities"
  (:require [leiningen.capsule.consts :as cc]
            [leiningen.core.main :as main]))

(defn ^:internal get-modes [project]
  "Extracts the modes map from a project"
  (get-in project cc/path-modes {}))

(defn ^:internal get-capsule-types [project]
  "Extracts, normalizes and returns the enabled capsule types sub-map, if any"
  (mapcat
    #(let [[k v] %] (if (sequential? v) (map (fn [e] [k e]) v) [%]))
    (seq (get-in project cc/path-types))))

(defn ^:internal get-capsule-default-mode-keyval [project]
  "Extracts and returns the default capsule mode sub-map, if any"
  (first (filter (fn [[_ v]] (:default v)) (get-modes project))))

(defn ^:internal get-capsule-default-mode [project]
  "Extracts and returns the default capsule mode name, if any"
  (second (get-capsule-default-mode-keyval project)))

(defn ^:internal get-capsule-default-mode-name [project]
  "Extracts and returns the default capsule mode name, if any"
  (first (get-capsule-default-mode-keyval project)))

(defn ^:internal get-project-without-default-mode [project]
  "Removes the default capsule mode from project"
  (update-in project cc/path-modes
    #(reduce-kv (fn [modes-map k v] (if (:default v) (dissoc modes-map k) modes-map)) (or %1 {}) (or %1 {}))))

(defn ^:internal artifact-to-string [[sym & more :as v]]
  "Returns the string representation of a Leiningen artifact"
  (if (string? sym)
    sym
    (let [nmspc (namespace sym)
          nmsym (name sym)
          classifier-idx (.indexOf v :classifier)]
      (str
        (if nmspc (str nmspc ":") "")
        nmsym ":" (second v) ; version
        (if classifier-idx (str ":" (nth v (+ classifier-idx 1))) "")))))

(defn ^:internal capsule-manifest [project]
  "Extracts the capsule-based manifest sub-map from a Leiningen project"
  (cc/kwd-capsule-manifest project))

(defn ^:internal capsule-manifest-value [project manifest-entry-name]
  "Returns the current capsule manifest value of the given entry name"
  (get (capsule-manifest project) manifest-entry-name))

(defn ^:internal add-to-manifest-if-path [project path manifest-entry-name f & [mode-keyword]]
  "Will add an entry's transformation through \"f\" in the given non-mode-aware project map path (if found) to the
  manifest map under the given mode-aware name"
  (let [value (get-in project path)
        transformed-value (f value)
        transformed-value-is-coll-or-string (or (coll? transformed-value) (instance? String transformed-value))]
    (if (and
          (or ; Only if there's some value
            (and transformed-value-is-coll-or-string (seq transformed-value)) ; Not empty
            (and (not transformed-value-is-coll-or-string) (not (nil? transformed-value))))
          (or ; Skip entries for modes with identical value to main section
            (nil? mode-keyword)
            (not= transformed-value (capsule-manifest-value project manifest-entry-name))))
      (do
        (main/debug "Inserting manifest entry" path mode-keyword manifest-entry-name transformed-value (cc/capsule-manifest-path project mode-keyword))
        (update-in project (cc/capsule-manifest-path project mode-keyword)
                   #(merge
                     (or % {})
                     { manifest-entry-name transformed-value })))
      (do
        (main/debug "Skipping manifest entry" path mode-keyword manifest-entry-name value transformed-value transformed-value-is-coll-or-string (cc/capsule-manifest-path project mode-keyword))
        project))))

(defn ^:internal add-to-manifest-if-mode-path [project path manifest-entry-name f & [mode-keyword]]
  "Will add an entry's transformation through \"f\" in the given mode-aware project map path (if found) to the
  manifest map under the given mode-aware name"
  (add-to-manifest-if-path
    project
    (cc/mode-aware-path project path mode-keyword)
    manifest-entry-name
    f
    mode-keyword))

(defn ^:internal add-to-manifest-if-mode-path-as-string [project path manifest-entry-name & [mode-keyword]]
  "Will add an entry's toString() in the given mode-aware project map path (if found) to the manifest map under
  the given mode-aware name"
  (add-to-manifest-if-mode-path
    project
    path manifest-entry-name
    #(if (not (nil? %)) (let [val (.toString %)] (if (seq val) val)))
    mode-keyword))

(defn ^:internal add-to-manifest [project manifest-entry-name manifest-entry-value & [mode-keyword]]
  "Will add the specified entry to the manifest map"
  (if (and
        (seq manifest-entry-value)
        (or ; Skip entries for modes with identical value to main section
          (nil? mode-keyword)
          (not= manifest-entry-value (capsule-manifest-value project manifest-entry-name))))
    (update-in project
               (cc/capsule-manifest-path project mode-keyword)
               #(merge % { manifest-entry-name manifest-entry-value }))
    project))

(defn ^:internal get-diff-section [project path & [mode-keyword]]
  "Returns the correct diff section based on mode, if specified, with fallback to global one"
  (or ; Either the mode-overridden, if present, or the global one
    (get-in project (cc/mode-aware-path project path mode-keyword))
    (get-in project (cons :capsule path))))


(defn ^:internal get-capsule-version [project]
  (let [v (get-in project (cc/mode-aware-path project cc/path-capsule-version))]
    (if (string? v) v "1.0")))

(defn ^:internal get-capsule-maven-version [project]
  (let [v (get-in project (cc/mode-aware-path project cc/path-capsule-maven-version))]
    (if (string? v) v "1.0")))

(defn ^:internal diff [items diff-spec]
  "Applies a lein-capsule-style diff-spec to a collection"
  (let [items (or (if (coll? items) items) [])]
    (cond
      (map? diff-spec)
        (let [add-spec (:add diff-spec)
              remove-spec (:remove diff-spec)
              items-after-add
                (cond
                  (coll? add-spec) (concat add-spec items)
                  (not (nil? add-spec)) (cons add-spec items)
                  :else items)]
          (seq
            (clojure.set/difference
              (set items-after-add)
              (cond
                (coll? remove-spec) (set remove-spec)
                (not (nil? remove-spec)) #{remove-spec}
                :else #{}))))
      (not (nil? diff-spec))
        diff-spec
      :else
        items)))

(defn ^:internal execution-boot-artifacts [project]
  "Get boot artifact specifications in global section as well as in types"
  (filter identity
          (cons
            (capsule-manifest-value project "Application")
            (map #(get "Application" %) (filter map? (vals (capsule-manifest project)))))))

(defn ^:internal get-capsules-output-dir [project]
  "Computes the capsules output dir starting from the project target folder based on specification or sensible defaults"
  (str (:target-path project) "/" (or (get-in project (cons :capsule cc/path-output-dir)) "capsules")))
