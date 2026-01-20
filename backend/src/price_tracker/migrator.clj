(ns price-tracker.migrator
  (:require [migratus.core :as migratus]
            [price-tracker.db :as db]))

(defn migratus-config
  []
  {:store :database
   :db {:connection-uri (db/normalize-jdbc-url
                         (or (System/getenv "DATABASE_URL")
                             "jdbc:postgresql://localhost:5432/price_tracker"))}
   :migration-dir "migrations"})

(defn migrate
  []
  (migratus/migrate (migratus-config)))

(defn -main
  [& _]
  (migrate))
