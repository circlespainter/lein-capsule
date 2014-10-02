;; TODO Remove this when https://github.com/cemerick/pomegranate/pull/59 is merged or (perhaps better) use directly fork

(ns leiningen.capsule.aether-eclipse
  (:import (org.eclipse.aether.repository RepositoryPolicy Authentication RemoteRepository)
           (org.sonatype.aether.util.repository DefaultProxySelector)
           (org.sonatype.aether.repository Proxy)
           (org.eclipse.aether.graph Dependency Exclusion)
           (org.eclipse.aether.artifact DefaultArtifact)
           (capsule.org.eclipse.aether.util.repository AuthenticationBuilder)))

(def update-policies {:daily RepositoryPolicy/UPDATE_POLICY_DAILY
                      :always RepositoryPolicy/UPDATE_POLICY_ALWAYS
                      :never RepositoryPolicy/UPDATE_POLICY_NEVER})

(def checksum-policies {:fail RepositoryPolicy/CHECKSUM_POLICY_FAIL
                        :ignore RepositoryPolicy/CHECKSUM_POLICY_IGNORE
                        :warn RepositoryPolicy/CHECKSUM_POLICY_WARN})

(defn- policy
  [policy-settings enabled?]
  (RepositoryPolicy.
    (boolean enabled?)
    (update-policies (:update policy-settings :daily))
    (checksum-policies (:checksum policy-settings :fail))))

(defn- set-policies
  [repo settings]
  (doto repo
    (.setPolicy true (policy settings (:snapshots settings true)))
    (.setPolicy false (policy settings (:releases settings true)))))

(defn- set-authentication
  "Calls the setAuthentication method on obj"
  [obj {:keys [username password passphrase private-key-file] :as settings}]
  (if (or username password private-key-file passphrase)
    (.setAuthentication obj
      (doto AuthenticationBuilder.
        (.addUsername username)
        (.addPassword password)
        (.addPrivateKey private-key-file)
        (.addPassword passphrase)))
    obj))

(defn- set-proxy
  [repo {:keys [type host port non-proxy-hosts ]
         :or {type "http"}
         :as proxy} ]
  (if (and repo host port)
    (let [prx-sel (doto (DefaultProxySelector.)
                    (.add (set-authentication (Proxy. type host port nil) proxy)
                          non-proxy-hosts))
          prx (.getProxy prx-sel repo)]
      (.setProxy repo prx))
    repo))

(defn make-repository
  "Produces an Aether RemoteRepository instance from Pomegranate-style repository information"
  [[id settings] proxy]
  (let [settings-map (if (string? settings)
                       {:url settings}
                       settings)]
    (doto (RemoteRepository. id
                             (:type settings-map "default")
                             (str (:url settings-map)))
      (set-policies settings-map)
      (set-proxy proxy)
      (set-authentication settings-map))))

(defn- group
  [group-artifact]
  (or (namespace group-artifact) (name group-artifact)))

(defn- coordinate-string
  "Produces a coordinate string with a format of
   <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>>
   given a lein-style dependency spec.  :extension defaults to jar."
  [[group-artifact version & {:keys [classifier extension] :or {extension "jar"}}]]
  (->> [(group group-artifact) (name group-artifact) extension classifier version]
       (remove nil?)
       (interpose \:)
       (apply str)))

(defn- exclusion
  [[group-artifact & {:as opts}]]
  (Exclusion.
    (group group-artifact)
    (name group-artifact)
    (:classifier opts "*")
    (:extension opts "*")))

(defn- normalize-exclusion-spec [spec]
  (if (symbol? spec)
    [spec]
    spec))

(defn- artifact
  [[group-artifact version & {:keys [scope optional exclusions]} :as dep-spec]]
  (DefaultArtifact. (coordinate-string dep-spec)))

(defn dependency
  "Produces an Aether Dependency instance from Pomegranate-style dependency information"
  [[group-artifact version & {:keys [scope optional exclusions]
                              :as opts
                              :or {scope "compile"
                                   optional false}}
    :as dep-spec]]
  (Dependency. (artifact dep-spec)
               scope
               optional
               (map (comp exclusion normalize-exclusion-spec) exclusions)))