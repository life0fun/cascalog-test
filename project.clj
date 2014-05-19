(defproject cascalog-test "1.0.0-SNAPSHOT"
  :description "cascalog testing"
  :dependencies [
  	[org.clojure/clojure "1.5.0"]
        [org.clojure/clojure-contrib "1.2.0"]
  	[cascalog "2.0.0"]]
  :jvm-opts ^:replace ["-Xms768m" "-Xmx768m"]
  :profiles { 
    :dev {:dependencies [[org.apache.hadoop/hadoop-core "1.1.2"]]}}
  )
