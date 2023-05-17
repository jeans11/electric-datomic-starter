(ns app.todo-list
  (:require #?(:clj [app.datomic-contrib :as db])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [datomic.api #?(:clj :as :cljs :as-alias) d]))

(e/def !conn)
(e/def db #?(:clj (new (db/latest-db> user/!conn user/!tx-queue)))) ; injected database ref; Electric defs are always dynamic

(e/defn TodoItem [id]
  (e/server
   (let [e (d/entity db id)
         status (:task/status e)]
     (e/client
      (dom/div
       (ui/checkbox
        (case status "active" false, "done" true)
        (e/fn [v]
          (e/server
           (e/discard
            @(d/transact !conn [{:db/id id
                                 :task/description (:task/description e) ; repeat
                                 :task/status (if v "done" "active")}]))))
        (dom/props {:id id}))
       (dom/label (dom/props {:for id}) (dom/text (e/server (:task/description e)))))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (dom/input (dom/props {:placeholder "Buy milk"})
             (dom/on "keydown" (e/fn [e]
                                 (when (= "Enter" (.-key e))
                                   (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                                     (new F v)
                                     (set! (.-value dom/node) "")))))))

(e/defn TodoCreate []
  (e/client
   (InputSubmit. (e/fn [v]
                   (e/server
                    (e/discard
                     @(d/transact !conn [{:task/description v
                                          :task/status "active"}])))))))

#?(:clj
   (defn todo-records [db]
     (->> (d/q '[:find (pull ?e [:db/id :task/description])
                 :where [?e :task/status]]
               db)
          (map first)
          (sort-by :task/description)
          vec)))

(comment (todo-records user/db))

#?(:clj
   (defn todo-count [db]
     (count (d/q '[:find ?e
                   :in $ ?status
                   :where [?e :task/status ?status]]
                 db "active"))))

(comment (todo-count user/db))

(e/defn Todo-list []
  (e/server
   (binding [!conn user/!conn]
     (e/client
      (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
      (dom/h1 (dom/text "minimal todo list"))
      (dom/p (dom/text "it's multiplayer, try two tabs"))
      (dom/div (dom/props {:class "todo-list"})
               (TodoCreate.)
               (dom/div {:class "todo-items"}
                        (e/server
                         (e/for-by :db/id [{:keys [db/id]} (e/offload #(todo-records db))]
                                   (TodoItem. id))))
               (dom/p (dom/props {:class "counter"})
                      (dom/span (dom/props {:class "count"})
                                (dom/text (e/server (e/offload #(todo-count db)))))
                      (dom/text " items left")))))))
