(ns utils
  (:require [clojurewerkz.spyglass.client :as c]
            [environ.core :refer [env]]
            [cairborne.core :refer [gen-cache-key encode decode]]
            [clojure.data.json :as json]))

(def samples #{50763 65290 31 43773})

(defn get-company-config
  ""
  [tmc id]
  (let [key (-> id (gen-cache-key "common") name)]
    (json/read-str (decode (c/get tmc key)))))

(defn get-test-companies
  "Let's pick few comapnies and see what we have in mc."
  [& ids]
  (let [tmc (c/text-connection (env :memcached-url))]
    (map (partial get-company-config tmc) ids)))

;; (json/write-str (zipmap samples (apply get-test-companies samples)))
;; (apply get-test-companies samples)
