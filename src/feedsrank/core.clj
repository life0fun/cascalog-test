; rank a feed, or msg in your news network based on timestamp and the credibility
; of the origin of msg.
; code from nathan's example.
; http://nathanmarz.com/blog/news-feed-in-38-lines-of-code-using-cascalog.html
;
; to run, lein uberjar to create a jar containing all its dependencies.
; hadoop jar feedsrank.jar feedsrank.core /tmp/follows /tmp/action /tmp/results
(ns feedsrank.core
  (:use cascalog.api)
  (:require [cascalog [vars :as v] [ops :as c]])
  (:gen-class))

; subquery to figure out for everybody how many followers one has. 
; take a path and ret a subquery that parse the file line-by-line emits follows-follower
; hfs-textline returns a Tap that reads text data from HDFS and emits each line of text as a 1-tuple.
; re-parse regex break each line of space(\s) separated fields into n-tuples.
(defn follows-data [dir]
  (let [source (hfs-textline dir)]
    (<- [?follows ?follower]
      (source ?line)
      (re-parse [#"[^\s]+"] ?line :> ?follows ?follower)
      (:distinct false))))

; get message timestamp
(defn to-long [num] (Long/parseLong num))
(defn actions-data [dir]
  (let [source (hfs-textline dir)]
    (<- [?person ?action ?ts]
      (source ?line)
      (re-parse [#"[^\s]+"] ?line :> ?person ?action ?ts-str)
      (to-long ?ts-str :> ?ts)
      (:distinct false))))

; subquery helper to query text file and parse space separated fields into n-tuples
(defn textfile-parser [dir nfields]
  (let [source [hfs-textline dir]
        outvars (v/gen-nullable-vars nfields)]  ; produce a vector of vars
      (<- outvars
        (source ?line)
        (re-parse [#"[^\s]+"] ?line :>> outvars) ; :> output var1 var2, :>> [var1, var2]
        (:distinct false))))

(defn follows-data [dir]
  (textfile-parser dir 2))

; define a subquery that queries input dat file and outputs a seq of 3-tuples
(defn actions-data [dir]
  (let [source (textfile-parser dir 3)]
    (<- [?person ?action ?ts]
      (source ?person ?action ?ts-str)
      (to-long ?ts-str :> ?ts)
      (:distinct false))))

; subquery to get num of follower for a person
(defn follower-num [follows person]
  (<- [?follower ?count]
    (follows ?follower person)
    (c/count ?count)))

; a buffer aggregator to take tuples and serialize into JSON string 
; takes all tuples, ret a seq of tuples as results, top 5 serialized into JSON.
(defbufferop mk-feed [tuples]
  [(pr-str (take 5 tuples))]) 

; score each action based on its origin and timestamp
(defn action-score [now-ms folls time-ms]
  (let [days-delta (div (- now-ms time-ms) 86400000)]
    (div folls (+ days-delta 1))))

; create an execution plan to join all subqueries 
(defn compute-news-feed [output-tap follows-dir actions-dir]
  (let [follows (follows-data follows-dir)  ; binding to get hold of parsed tuples
        actions (actions-data actions-dir)]
    ; now need to execute subquery output to tap with projected fields
    ; for each per ?p2 followed by ?p, 
    (?<- output-tap [?person ?feed]
      (follows ?person ?p2)  ; scan through follows table of all p2 person follows
      (actions ?p2 ?a ?ts))  ; join action data to get my followers data (on p2)
      ((follower-num follows ?p2) ?p2 ?fcolls)  ; query tot num of followers
      (action-score (System/currentTimeMillis) ?folls ?time :> ?score)
      (:sort ?score) (:reverse true)
      (mk-feed ?p2 ?action ?time :> ?feed)))


      