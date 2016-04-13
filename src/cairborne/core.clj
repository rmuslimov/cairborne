(ns cairborne.core
  (:gen-class)
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [com.stuartsierra.component :as cmp]
            [environ.core :refer [env]]
            [heroku-database-url-to-jdbc.core :refer [korma-connection-map]]
            [korma
             [core :as k]
             [db :as kd]]))

(defn main-system
  "Whole system here."
  []
  (cmp/system-map))

(kd/defdb db (korma-connection-map (env :database-url)))

(k/defentity companies
  (k/table :accounts_bcdcompanyprofile)
  (k/entity-fields :id :title :config :parent_id :level))

(k/defentity blobs
  (k/table :accounts_blobconfigvalue)
  (k/entity-fields :id :value))

(defn gen-cache-key
  [entity-id subsystem]
  (keyword (format "hyatt_entity_%s_%s" entity-id subsystem)))

(defn merge-configs
  "Return merged config basedefault on parents and common."
  [pconf {common :common :or {:common {}} :as conf}]
  (let [merge-fn
        (fn [k] (merge (get conf k {}) (get pconf k {}) common))]
    {:aft (merge-fn :aft)
     :cbt (merge-fn :cbt)
     :mobile (merge-fn :mobile)
     :common (merge (:common pconf) common)}))

(defn put-delays
  "Add :calc field to map, and prepare for calculations"
  [rows]
  (let [p (promise)
        calc
        (fn [{parent-id :parent_id c :config :as row}]
          (delay
           (if-not
               parent-id (merge-configs {} c)
               (merge-configs @(-> @p (get parent-id) :calc) c))))]
    (deliver p (zipmap (keys rows) (map #(assoc % :calc (calc %)) (vals rows))))
    @p))

(defn get-companies
  "Get whole companies list, asctime map with id and parse config."
  []
  (let [rows (k/select
              companies
              (k/where {:is_deleted false})
              (k/order :level))]
    (zipmap
     (map :id rows)
     (map (fn [r]
            (update r :config
                   #(or (some-> % json/read-str walk/keywordize-keys) {})))
          rows))))

(defn set-cache!
  "Put processed rows to memcached."
  [& rows]
  (let [blobs (k/select blobs)]
    (flatten
     (for [{id :id c :calc} rows]
       (map (fn [[sub v]] [(gen-cache-key id (name sub)) (json/write-str v)]) @c))
     )))

;; (time (def data (-> (get-companies) (put-delays))))
;; (time (count (doall (for [[id e] data] [id @(:calc e)]))))
;; (time (apply hash-map (set-cache! (get data 41876) (get data 41875))))
;; @(:calc (get data 48605))

(defn -main
  [& args]
  "Nothing so far.")
