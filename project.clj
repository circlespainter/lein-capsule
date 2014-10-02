(defproject lein-capsule "0.1.0-SNAPSHOT"
	:description "A Leiningen plugin for Parallel Universe's Capsule"
	:url "https://github.com/circlespainter/lein-capsule"
	:license {:name "Eclipse Public License"
						:url "http://www.eclipse.org/legal/epl-v10.html"}

	:dependencies [[lein-pprint "1.1.1"]
                 [com.cemerick/pomegranate "0.3.1-SNAPSHOT"]
                 [co.paralleluniverse/capsule-build "0.10.0-SNAPSHOT"]]

	:eval-in-leiningen true)