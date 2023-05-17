(ns app.datomic-contrib
  (:require
   [datomic.api :as d]
   [missionary.core :as m])
  (:import (java.util.concurrent LinkedBlockingQueue)))

(def schema
  [{:db/ident :task/status
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/description
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])

(defn latest-db> [!conn queue]
  (->> (m/observe
        (fn [!]
          (! (d/db !conn))
          (let [t (Thread. ^Runnable
                   (fn []
                     (! (:db-after (.take ^LinkedBlockingQueue queue)))
                     (recur)))]
            (.start t)
            #(do (.interrupt t)
                 (.join t)))))
       (m/relieve {})))
