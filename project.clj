(defproject lein-capsule "0.1.0-SNAPSHOT"

	:description "A Leiningen plugin for Parallel Universe's Capsule"

	:url "https://github.com/circlespainter/lein-capsule"

  ; TODO Check if it works for POM generation to have two under :licenses, Leiningen's sample doesn't list this case
	:licenses [{:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
             {:name "GNU Lesser General Public License - v 3" :url "http://www.gnu.org/licenses/lgpl.html"}]

  ; TODO Check if it works to have it outside of license(s), Leiningen's sample doesn't list this case
  :distribution :repo

  :min-lein-version "2.4.3"

  :plugins [[lein-midje "3.1.1"]
            [codox "0.6.4"]
            [lein-marginalia "0.7.1"]]

	:dependencies [[org.eclipse.aether/aether-api "1.0.0.v20140518"]
                 [co.paralleluniverse/capsule "0.10.0-SNAPSHOT"]
                 [co.paralleluniverse/capsule-build "0.10.0-SNAPSHOT"]
                 [co.paralleluniverse/capsule-util "0.10.0-SNAPSHOT"]]

	:eval-in-leiningen true)