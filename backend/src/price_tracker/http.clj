(ns price-tracker.http
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [price-tracker.api :as api]
            [price-tracker.rate-limit :as rate-limit]
            [price-tracker.responses :as responses]
            [reitit.ring :as ring]
            [ring.middleware.cors :as cors]
            [ring.middleware.params :as params]
            [taoensso.timbre :as log]))

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
        (log/error e "Unhandled error during request.")
        (responses/error-response 500 "internal_error" "Unexpected error.")))))

(defn- wrap-request-logging
  [handler]
  (fn [req]
    (let [start (System/nanoTime)
          resp (handler req)
          duration-ms (/ (- (System/nanoTime) start) 1000000.0)
          status (or (:status resp) 200)
          method (-> req :request-method name str/upper-case)
          uri (:uri req)
          message (format "%s %s -> %d (%.1fms)" method uri status duration-ms)]
      (if (>= status 400)
        (log/warn message)
        (log/info message))
      resp)))

(defn routes
  [config ds]
  [["/health" {:get health-handler}]
   ["/api/products"
    ["" {:get (api/list-products ds)
         :post (api/create-product ds config)}]
    ["/:id"
     ["" {:get (api/get-product ds)
          :delete (api/delete-product ds)}]
     ["/prices" {:get (api/get-price-history ds)}]
     ["/refresh" {:post (api/refresh-product ds config)}]
     ["/fetch" {:post (api/fetch-product ds config)}]]]])

(defn- wrap-cors-if-needed
  [handler {:keys [allowed-origins]}]
  (let [origin->pattern (fn [origin]
                          (if (instance? java.util.regex.Pattern origin)
                            origin
                            (re-pattern (str "^" (java.util.regex.Pattern/quote (str origin)) "$"))))]
    (if (seq allowed-origins)
      (cors/wrap-cors handler
                      :access-control-allow-origin (mapv origin->pattern allowed-origins)
                      :access-control-allow-methods [:get :post :put :delete :options])
      handler)))

(defn handler
  [config ds]
  (let [router (ring/router
                (routes config ds))
        default-handler (ring/create-default-handler)]
    (-> (ring/ring-handler router default-handler)
        (params/wrap-params)
        (rate-limit/wrap-rate-limit {:limit-per-minute (get-in config [:fetch :rate-limit-per-minute])})
        (wrap-json-body)
        (wrap-cors-if-needed (:cors config))
        (wrap-exceptions)
        (wrap-request-logging))))
