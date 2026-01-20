(ns price-tracker.responses
  (:require [cheshire.core :as json]
            [ring.util.response :as response]))

(defn json-response
  [data]
  (-> (response/response (json/encode data))
      (response/content-type "application/json")))

(defn status-json
  [status data]
  (-> (json-response data)
      (response/status status)))

(defn error-response
  ([status type message]
   (error-response status type message nil))
  ([status type message details]
   (status-json status
                {:error (cond-> {:type type
                                 :message message}
                          details (assoc :details details))})))

(defn no-content
  []
  (-> (response/response nil)
      (response/status 204)))
