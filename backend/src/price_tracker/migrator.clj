(ns price-tracker.migrator
  (:require [migratus.core :as migratus]))

(defn migratus-config
  []
  {:store :database
   :db {:connection-uri (or (System/getenv "DATABASE_URL")
                            "jdbc:postgresql://localhost:5432/price_tracker")}
   :migration-dir "migrations"})

(defn migrate
  []
  (migratus/migrate (migratus-config)))
