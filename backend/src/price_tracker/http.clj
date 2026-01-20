(ns price-tracker.http
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [price-tracker.api :as api]
            [price-tracker.rate-limit :as rate-limit]
            [price-tracker.responses :as responses]
            [reitit.ring :as ring]
            [ring.middleware.cors :as cors]
            [ring.middleware.params :as params]))

(defn- health-handler
  [_]
  (responses/json-response {:status "ok"}))

(defn- wrap-json-body
  [handler]
  (fn [req]
    (let [content-type (get-in req [:headers "content-type"] "")]
      (if (str/includes? content-type "application/json")
        (let [raw (slurp (:body req))
              raw-trim (str/trim raw)]
          (if (str/blank? raw-trim)
            (handler (assoc req :json-body nil))
            (try
              (handler (assoc req :json-body (json/parse-string raw-trim true)))
              (catch Exception _
                (responses/error-response 400 "invalid_json" "Malformed JSON payload.")))))
        (handler req)))))

(defn- wrap-exceptions
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (binding [*out* *err*]
          (println "Unhandled error:" (.getMessage e)))
        (responses/error-response 500 "internal_error" "Unexpected error.")))))

(defn routes
  [ds]
  [["/health" {:get health-handler}]
   ["/api/products"
    {:get (api/list-products ds)
     :post (api/create-product ds)}]
   ["/api/products/:id"
    {:get (api/get-product ds)
     :delete (api/delete-product ds)}]
   ["/api/products/:id/prices"
    {:get (api/get-price-history ds)}]
   ["/api/products/:id/refresh"
    {:post (api/refresh-product ds)}]])

(defn- wrap-cors-if-needed
  [handler {:keys [allowed-origins]}]
  (if (seq allowed-origins)
    (cors/wrap-cors handler
                    :access-control-allow-origin allowed-origins
                    :access-control-allow-methods [:get :post :put :delete :options])
    handler))

(defn handler
  [config ds]
  (let [router (ring/router
                (routes ds))]
    (-> (ring/ring-handler router)
        (params/wrap-params)
        (rate-limit/wrap-rate-limit {:limit-per-minute (get-in config [:fetch :rate-limit-per-minute])})
        (wrap-json-body)
        (wrap-cors-if-needed (:cors config))
        (wrap-exceptions))))
