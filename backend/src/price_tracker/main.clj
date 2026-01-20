(ns price-tracker.main
  (:require [integrant.core :as ig]
            [price-tracker.system :as system])
  (:gen-class))

(defonce runtime (atom nil))

(defn start
  []
  (let [sys (ig/init (system/system-config))]
    (reset! runtime sys)
    sys))

(defn stop
  []
  (when-let [sys @runtime]
    (ig/halt! sys)
    (reset! runtime nil)))

(defn -main
  [& _]
  (start))
