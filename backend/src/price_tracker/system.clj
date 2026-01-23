(ns price-tracker.system
  (:require [integrant.core :as ig]
            [price-tracker.config :as config]
            [price-tracker.db :as db]
            [price-tracker.http :as http]
            [price-tracker.logging :as logging]
            [ring.adapter.jetty :as jetty]))

(defmethod ig/init-key :app/config
  [_ _]
  (config/load-config))

(defmethod ig/init-key :app/handler
  [_ {:keys [config db]}]
  (http/handler config db))

(defmethod ig/init-key :app/logging
  [_ {:keys [config]}]
  (logging/configure! config))

(defmethod ig/init-key :app/db
  [_ {:keys [config]}]
  (db/datasource (get-in config [:db :database-url])))

(defmethod ig/halt-key! :app/db
  [_ ds]
  (db/close-datasource ds))

(defmethod ig/init-key :app/http
  [_ {:keys [config handler]}]
  (let [port (get-in config [:http :port])
        host (get-in config [:http :host])]
    (jetty/run-jetty handler {:host host
                              :port port
                              :join? false})))

(defmethod ig/halt-key! :app/http
  [_ server]
  (.stop server))

(defn system-config
  []
  {:app/config {}
   :app/logging {:config (ig/ref :app/config)}
   :app/db {:config (ig/ref :app/config)}
   :app/handler {:config (ig/ref :app/config)
                 :logging (ig/ref :app/logging)
                 :db (ig/ref :app/db)}
   :app/http {:config (ig/ref :app/config)
              :handler (ig/ref :app/handler)}})
