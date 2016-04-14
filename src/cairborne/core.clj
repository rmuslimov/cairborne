(ns cairborne.core
  (:gen-class)
  (:require [clojure.data.json :as json]
            [clojure.walk :as walk]
            [com.stuartsierra.component :as cmp]
            [environ.core :refer [env]]
            [heroku-database-url-to-jdbc.core :refer [korma-connection-map]]
            [clojurewerkz.spyglass.client :as c]
            [clojure.data.codec.base64 :as b64]
            [korma
             [core :as k]
             [db :as kd]]
            [manifold.deferred :as d]))

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

(defn gen-blob-key
  [id]
  (keyword (format "hyatt_blob_value_%s" id)))

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

(defn config-to-keys
  ""
  [entity-id config]
  (apply hash-map
         (mapcat
          identity
          (for [sys #{:aft :cbt :common :mobile}]
            (list (name (gen-cache-key entity-id (name sys)))
                  (get config sys))))))

(defn set-cache!
  "Put processed rows to memcached."
  [& rows]
  (let [tmc (c/bin-connection (env :memcached-url))]
    (doseq [[id {calc :calc :as row}] rows]
      (let [pairs (config-to-keys id @calc)]
        (doseq [[k v] pairs]
          (c/set tmc k 3000 (json/write-str v)))))))

(defn encode-django-binary
  "Django uses binary field let's encode them."
  [{v :value :as row}]
  (let [newv
        (when v
          (let [vstr (String. v)]
            (try
              (json/read-str vstr)
              (catch Exception vstr))))]
    (assoc row :value newv)))

(defn set-blobs!
  []
  (let [tmc (c/bin-connection (env :memcached-url))
        blobs (map encode-django-binary (k/select blobs))]
    (doseq [{:keys [id value]} blobs]
      (when value
        (c/set tmc (name (gen-blob-key id)) 3000 (json/write-str value))))))

(defn apply-set-cache!
  "Just put data to cache in parallel threads."
  [data]
  (apply d/zip
         (for [chunk (partition-all 1e3 data)]
           (future (apply set-cache! chunk)))))

(defn rebuild-hyatt-fast
  []
  (d/zip
   (future (set-blobs!))
   (d/chain
    (-> (get-companies) (put-delays))
    apply-set-cache!)))

;; (time (def tree (-> (get-companies) (put-delays))))
;; (time (set-blobs!))
;; (time (apply-set-cache! tree))
;; (map encode-django-binary (k/select blobs (k/where {:id 1154})))

;; Call rebuild hyatt process
;; (time @(rebuild-hyatt-fast))
;; "Elapsed time: 1437.536992 msecs"
;; "Elapsed time: 1406.571521 msecs"
;; "Elapsed time: 1267.539232 msecs"

(defn -main
  [& args]
  "Nothing so far.")
