(ns price-tracker.config
  (:require [clojure.string :as str]))

(defn- env
  [key default]
  (or (System/getenv key) default))

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
  {:http {:port (env-int "PORT" 3000)}
   :db {:database-url (env "DATABASE_URL" nil)}
   :logging {:level (env "LOG_LEVEL" "info")}
   :fetch {:timeout-ms (env-int "FETCH_TIMEOUT_MS" 10000)
           :rate-limit-per-minute (env-int "RATE_LIMIT_PER_MINUTE" 60)
           :allowed-domains (parse-origins (env "ALLOWED_DOMAINS" nil))
           :user-agent (env "FETCH_USER_AGENT" nil)}
   :cors {:allowed-origins (parse-origins (env "CORS_ALLOWED_ORIGINS" nil))}})
