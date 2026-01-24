(ns price-tracker.config
  (:require [clojure.string :as str]
            [price-tracker.fetch :as fetch]))

(defn- env
  [key default]
  (or (System/getenv key) default))

(defn- env-str
  [key default]
  (let [raw (System/getenv key)]
    (if (seq (str/trim (or raw "")))
      raw
      default)))

(defn- env-int
  [key default]
  (let [raw (env key nil)]
    (if (seq raw)
      (Integer/parseInt raw)
      default)))

(defn- parse-origins
  [raw]
  (when (seq raw)
    (->> (str/split raw #",")
         (map str/trim)
         (remove empty?)
         vec)))

(defn load-config
  []
  (let [fetch-config {:rate-limit-per-minute (env-int "RATE_LIMIT_PER_MINUTE" 60)
                      :timeout-ms (env-int "FETCH_TIMEOUT_MS" 8000)
                      :user-agent (env-str "FETCH_USER_AGENT" "PriceTrackerBot/0.1 (+local-only)")
                      :allowed-domains (parse-origins (env "ALLOWED_DOMAINS" nil))
                      :denylist-path (env "DENYLIST_CONFIG" "resources/denylist.yml")}
        denylist (fetch/load-denylist {:fetch fetch-config})]
    {:http {:host (env "HOST" "127.0.0.1")
            :port (env-int "PORT" 3000)}
     :db {:database-url (env "DATABASE_URL" nil)}
     :logging {:level (env "LOG_LEVEL" "info")}
     :fetch (assoc fetch-config :denylist denylist)
     :cors {:allowed-origins (parse-origins (env "CORS_ALLOWED_ORIGINS" nil))}}))
