(ns user ; Must be ".clj" file, Clojure doesn't auto-load user.cljc
  (:require clojure.java.io
            [datomic.api :as d]
            [app.datomic-contrib :as db]))

; lazy load dev stuff - for faster REPL startup and cleaner dev classpath
(def start-electric-server! (delay @(requiring-resolve 'electric-server-java8-jetty9/start-server!)))
(def shadow-start! (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
(def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))
(def db-uri "datomic:mem://db")

(defn start-datomic! []
  (d/connect db-uri))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(def !conn)
(defonce !db (atom nil))
(def !tx-queue)
(def !electric-server)

; Server-side Electric userland code is lazy loaded by the shadow build.
; WARNING: make sure your REPL and shadow-cljs are sharing the same JVM!

(defn main [& args]
  (println "Starting Datomic...")
  (d/delete-database db-uri)
  (d/create-database db-uri)
  (alter-var-root #'!conn (constantly (start-datomic!)))
  (alter-var-root #'!tx-queue (constantly (d/tx-report-queue !conn)))
  @(d/transact !conn app.datomic-contrib/schema)
  (reset! !db (d/db !conn))
  ;; datomic tx queue allows only single consumer
  ;; we spawn a single consumer here and feed the new db into an atom
  ;; clients can now watch this atom
  (future (let [q (d/tx-report-queue !conn)]
            (loop []
              (reset! !db (:db-after (.take ^java.util.concurrent.LinkedBlockingQueue q)))
              (recur))))
  (comment (.close !conn))
  (println "Starting Electric compiler...")
  (@shadow-start!) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  (println "Starting Electric server...")
  (alter-var-root #'!electric-server (constantly (@start-electric-server! electric-server-config)))
  (comment (.stop !electric-server)))

(comment
  (main) ; Electric Clojure(JVM) REPL entrypoint
  (hyperfiddle.rcf/enable!) ; turn on RCF after all transitive deps have loaded

  (type !conn)
  (def db (d/db !conn))

  (shadow.cljs.devtools.api/repl :dev) ; shadow server hosts the cljs repl
  ; connect a second REPL instance to it
  ; (DO NOT REUSE JVM REPL it will fail weirdly)
  (type 1))
