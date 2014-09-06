(defproject lein-capsule-test "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
	:plugins [[lein-capsule "0.1.0-SNAPSHOT"] [lein-pprint "1.1.1"]]
  :dependencies [[org.clojure/clojure "1.6.0"]]

	;;; For test
	:profiles {:bla {:jvm-opts "-server"}}

	;;; Capsule plugin configuration section, optional
	:capsule {
	;;; Optional
		:min-plugin-version "0.1.0-SNAPSHOT" ; TODO Implement

	;;; Optional, corresponds 1:1 to Log-Level manifest entry
		:log-level ""

	;;; Optional, defaults to <jarbasename>-<capsuletype>-capsule.jar, corresponds 1:1 to Log-Level manifest entry
		:name "capsule.jar"

	;;; Optional, only :this is default behaviour; if more than one type is configured, at least one must override :name
		:types {
			;; Optional, can override anything
			:thin {
			}
			;; Optional, can override anything, will trigger building a fat capsule
			:fat {
				:name "capsule-fat.jar"
			}
		}

	;;; Optional, check https://github.com/puniverse/capsule#application-id for defaults
		:application {
			;; Optional, corresponds 1:1 to Application-Name manifest entry, check https://github.com/puniverse/capsule#application-id for defaults
			:name "lein-capsule-test"
			;; Optional, corresponds 1:1 to Application-Version manifest entry, check https://github.com/puniverse/capsule#application-id for defaults
			:version "0.1.0-SNAPSHOT"
		}

	;;; Optional, capsule modes, each of them can override anything except types and application settings
		:profiles {
			:mode1 {
				;; Optional, will ignore top-level settings and use this mode as default, defaults to false
				:default false
			}
		}

	;;; Optional
		:execution {
			;; Optional
			:boot {
				;; Optional, check https://github.com/puniverse/capsule#application-id for defaults
				:main-class "Capsule"

				;; Optional, check https://github.com/puniverse/capsule#capsules-cache for defaults
				:extract-capsule false

				;; Optional, if missing it'll be Leiningen's :main unless one of the following two is specified
				:clojure-ns "main"

				;; Optional, applicable only if application-ns nor Leiningen's main; if neither is present then
				;; the next one must be
				:scripts {
					;; Mandatory
					:unix ""
					;; Mandatory
					:windows ""
				}

				;; Optional, applicable only if none of the above is; in that case, if missing, the project's
				;; complete artifact ID will be used
				:artifact ""
			}

			:args []

			;; Optional, check https://github.com/puniverse/capsule#selecting-the-java-runtime for defaults
			:runtime {
				;; Optional
				:java-version ""
				;; Optional
				:min-java-version ""
				;; Optional
				:min-update-version ""
				;; Optional
				:jdk-required false

				;; Optional
				:jvm-args []

				;; Optional
				:system-properties {}

				;; Optional
				:environment-variables {}

				;; Optional
				:agents [
					;; Optional
					{ :embedded {
						;; Mandatory
						:jar ""
						;; Optional
						:params []
					}}
					{ :artifact {
						;; Mandatory
						:id ""
						;; Optional
						:params []
					}}
				]

				:paths {
					;; Optional
					:embedded-classpath {
						;; Optional
						:override []
					}

					;; Optional
					:boot-classpath {
						;; Optional, will take precedence if the next one is present too
						:prepend []
						;; Optional
						:override []
					}

					:native-library-path {
						;; Optional, will take precedence if the next one is present too
						:append []
						;; Optional
						:prepend []
					}
				}

				:security {
					:policy {
						;; Optional, will take precedence if the next one is present too
						:prepend ""
						;; Optional
						:override ""
					}
				}
			}
		}

	;;; Optional, check https://github.com/puniverse/capsule#maven-dependencies for defaults
		:dependencies {
			;; Optional
			:allow-snapshots false

			;; Optional
			:repositories [central]

			;; Optional
			:artifacts {
				;; Optional
				:jvm {
					;; Optional
					:add []
					;; Optional
					:remove []
					;; Optional
					:replace []
				}
				;; Optional
				:native {
					;; Optional
					:windows []
					;; Optional
					:osx []
					;; Optional
					:linux []
				}
			}
		}
	})