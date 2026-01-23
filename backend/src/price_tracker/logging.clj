(ns price-tracker.logging
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn- normalize-level
  [value]
  (let [raw (some-> value str/lower-case keyword)]
    (if (contains? #{:trace :debug :info :warn :error :fatal} raw)
      raw
      :info)))

(defn configure!
  [config]
  (let [level (normalize-level (get-in config [:logging :level]))]
    (log/merge-config! {:min-level level})
    (log/info "Logging configured with level" (name level))))
