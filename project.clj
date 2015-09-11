(def capsule-version "1.0")

(defproject lein-capsule "0.2.0"

  :description "A Leiningen plugin for Parallel Universe's Capsule"

  :url "https://github.com/circlespainter/lein-capsule"

  ; TODO Check if it works for POM generation to have two under :licenses, Leiningen's sample doesn't list this case
  :licenses [{:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
             {:name "GNU Lesser General Public License - v 3" :url "http://www.gnu.org/licenses/lgpl.html"}]

  ; TODO Check if it works to have it outside of license(s), Leiningen's sample doesn't list this case
  :distribution :repo

  :min-lein-version "2.5.0"

  :source-paths      ["src/main/clojure"]
  :test-paths        ["src/test/clojure"]
  :resource-paths    ["src/main/resources"]
  :java-source-paths ["src/main/java"]

  :profiles { :dev { :dependencies [[midje "1.6.3"]] } }

  :plugins [
             ; [lein-pprint "1.1.1"]
             ; [lein-marginalia "0.8.0"]
             ; [lein-html5-docs "2.2.0"]
             ; [codox "0.8.10"]
             ; [lein-midje "3.1.3"]
           ]

  :dependencies [[co.paralleluniverse/capsule ~capsule-version]
                 [co.paralleluniverse/capsule-util ~capsule-version]
                 [co.paralleluniverse/capsule-maven ~capsule-version]]

  :eval-in-leiningen true)
