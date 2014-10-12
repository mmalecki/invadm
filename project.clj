(defproject invadm "0.0.0",
  :description "Manage invoices from command line",
  :url "https://github.com/mmalecki/invadm"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.5"]
                 [clj-time "0.8.0"]
                 [org.clojure/tools.cli "0.3.1"]]
  :main ^:skip-aot invadm.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
