(defproject lein-capsule "0.1.0-SNAPSHOT"
	:description "A Leiningen plugin for Parallel Universe's Capsule"
	:url "https://github.com/circlespainter/lein-capsule"
	:license {:name "Eclipse Public License"
						:url "http://www.eclipse.org/legal/epl-v10.html"}

	:dependencies [[com.cemerick/pomegranate "0.3.0"]
                 [org.eclipse.aether/aether-api "1.0.0.v20140518"]
                 [co.paralleluniverse/capsule "0.10.0-SNAPSHOT"]
                 [co.paralleluniverse/capsule-build "0.10.0-SNAPSHOT"]
                 [co.paralleluniverse/capsule-util "0.10.0-SNAPSHOT"]]

	:eval-in-leiningen true)