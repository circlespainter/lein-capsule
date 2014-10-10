; TODO Review for compliance with https://github.com/bbatsov/clojure-style-guide

(ns ^{ :author "circlespainter" :internal true } leiningen.capsule.utils
  "Various capsule-building utilities"
  (:require [leiningen.capsule.consts :as cc]))

(defn ^:internal get-capsule-types [project]
  "Extracts, normalizes and returns the enabled capsule types sub-map, if any"
  (mapcat
    #(let [[k v] %] (if (sequential? v) (map (fn [e] [k e]) v) [%]))
    (seq (get-in project cc/path-types))))

(defn ^:internal get-capsule-default-mode [project]
  "Extracts and returns the default capsule mode sub-map, if any"
  (reduce-kv (fn [i _ v] (merge i (if (:default v) (dissoc v :default) {}))) {} (get-in project cc/path-modes)))

(defn ^:internal get-capsule-default-mode-name [project]
  "Extracts and returns the default capsule mode sub-map, if any"
  (reduce-kv (fn [i k v] (str i (if (:default v) k ""))) "" (get-in project cc/path-modes)))

(defn ^:internal get-project-without-default-mode [project]
  "Removes the default capsule mode from project"
  (update-in project cc/path-modes
    #(reduce-kv (fn [modes-map k v] (if (:default v) (dissoc modes-map k) modes-map)) %1 %1)))

(defn ^:internal artifact-to-string [[sym ver]]
  "Returns the string representation of a Leiningen artifact"
  (let [nmspc (namespace sym)
        nmsym (name sym)]
    (str
      (if nmspc (str nmspc ":") "")
      nmsym ":" ver)))

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
      (update-in project (cc/capsule-manifest-path project mode-keyword)
                 #(merge
                   (or % {})
                   { manifest-entry-name transformed-value }))
      project)))

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

(defn ^:internal diff-section [project path & [mode-keyword]]
  "Returns the correct diff section based on mode, if specified, with fallback to global one"
  (or ; Either the type-overridden, if present, or the global one
    (get-in project (cc/mode-aware-path project path mode-keyword))
    (get-in project (cons :capsule path))))

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

(defn ^:internal lein-agents-to-capsule-artifact-agents [project]
  "Builds a collection of capsule-spec-format artifact agents from project's"
  ; TODO Bootclasspath agent artifacts not supported by Capsule AFAIK, but check
  (map #({:artifact %}) (filter #(not (:bootclasspath %)) (:java-agents project))))

(defn ^:internal has-artifact-agents [project]
  "Tell if there's any artifact agent specification in global section or in types"
  (let [agents (lein-agents-to-capsule-artifact-agents project)]
    (not-every? empty?
                (cons
                  (diff
                    agents
                    (get-in project (cons :capsule cc/path-runtime-agents)))
                  (map
                    #(diff
                      agents
                      (get-in % cc/path-runtime-agents))
                    (vals (get-in project cc/path-types)))))))