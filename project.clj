(defproject cairborne "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.stuartsierra/component "0.3.1"]
                 [environ "1.0.2"]
                 [korma "0.4.2"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [org.danielsz/system "0.2.1-SNAPSHOT"]
                 [postgresql "9.3-1102.jdbc41"]
                 [reloaded.repl "0.2.1"]
                 [heroku-database-url-to-jdbc "0.2.2"]
                 [org.clojure/data.json "0.2.6"]
                 [clojurewerkz/spyglass "1.1.0-beta3"]
                 [org.clojure/data.codec "0.1.0"]
                 [manifold "0.1.4"]]
  :main ^:skip-aot cairborne.core
  :plugins [[lein-environ "1.0.2"]]
  :target-path "target/%s"
  :profiles {:dev {:source-paths ["dev"]}
             :uberjar {:aot :all}}
  :env {:database-url "postgres://postgres@lf:5432/airborne"
        :memcached-url "127.0.0.1:11211"})
