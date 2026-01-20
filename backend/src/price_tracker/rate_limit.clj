(ns price-tracker.rate-limit
  (:require [clojure.string :as str]
            [price-tracker.responses :as responses]))

(def ^:private window-ms 60000)

(defn- now-ms
  []
  (System/currentTimeMillis))

(defn- prune
  [entries cutoff]
  (filterv #(> % cutoff) entries))

(defn wrap-rate-limit
  [handler {:keys [limit-per-minute]}]
  (if (and limit-per-minute (pos? limit-per-minute))
    (let [state (atom {})]
      (fn [req]
        (if-not (str/starts-with? (or (:uri req) "") "/api")
          (handler req)
          (let [client (or (:remote-addr req) "unknown")
                endpoint (:uri req)
                key [endpoint client]
                now (now-ms)
                allowed? (atom false)]
            (swap! state
                   (fn [entries]
                     (let [recent (prune (get entries key []) (- now window-ms))]
                       (if (>= (count recent) limit-per-minute)
                         (do (reset! allowed? false)
                             (assoc entries key recent))
                         (do (reset! allowed? true)
                             (assoc entries key (conj recent now)))))))
            (if @allowed?
              (handler req)
              (responses/error-response 429 "rate_limited" "Too many requests."))))))
    handler))
