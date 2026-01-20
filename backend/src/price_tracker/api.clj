(ns price-tracker.api
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [price-tracker.responses :as responses]
            [price-tracker.store :as store])
  (:import [java.net URI]
           [java.time Instant]
           [java.util UUID]))

(defn- parse-uuid
  [value]
  (try
    (UUID/fromString value)
    (catch IllegalArgumentException _
      nil)))

(defn- parse-url
  [value]
  (when (and (string? value)
             (<= (count value) 2048))
    (try
      (let [uri (URI. value)
            scheme (some-> (.getScheme uri) str/lower-case)
            host (.getHost uri)]
        (when (and host (#{"http" "https"} scheme))
          {:uri uri :host (str/lower-case host)}))
      (catch Exception _
        nil))))

(defn- normalize-price
  [value]
  (cond
    (nil? value) nil
    (number? value) (bigdec value)
    (string? value) (try
                      (bigdec value)
                      (catch NumberFormatException _
                        nil))
    :else nil))

(defn- create-product-input
  [body]
  (let [{:keys [url title price currency]} body
        parsed (parse-url url)
        normalized-price (normalize-price price)]
    (cond
      (nil? body)
      {:error {:type "invalid_json" :message "Request body must be JSON."}}

      (or (str/blank? url) (nil? parsed))
      {:error {:type "validation_error" :message "Invalid URL."}}

      (str/blank? title)
      {:error {:type "validation_error" :message "Title is required."}}

      (nil? normalized-price)
      {:error {:type "validation_error" :message "Price is required and must be numeric."}}

      :else
      {:ok {:url url
            :canonical-url nil
            :domain (:host parsed)
            :title title
            :currency currency
            :price normalized-price}})))

(defn create-product
  [ds]
  (fn [req]
    (let [{:keys [ok error]} (create-product-input (:json-body req))]
      (if error
        (responses/error-response 422 (:type error) (:message error))
        (jdbc/with-transaction [tx ds]
          (let [checked-at (Instant/now)
                product (store/create-product! tx (select-keys ok [:url :canonical-url :domain :title :currency]))
                snapshot (store/insert-price-snapshot! tx
                                                       {:product-id (:id product)
                                                        :price (:price ok)
                                                        :currency (:currency ok)
                                                        :source "manual"
                                                        :raw-price-text nil
                                                        :checked-at checked-at})]
            (store/update-last-checked! tx {:product-id (:id product)
                                            :checked-at checked-at})
            (responses/status-json
             201
             {:id (:id product)
              :title (:title product)
              :price (:price snapshot)
              :currency (:currency snapshot)
              :domain (:domain product)
              :lastCheckedAt (:checked_at snapshot)})))))))

(defn list-products
  [ds]
  (fn [_]
    (responses/json-response
     (map (fn [row]
            {:id (:id row)
             :title (:title row)
             :domain (:domain row)
             :currentPrice (:current_price row)
             :currency (or (:current_currency row) (:currency row))
             :lastCheckedAt (:last_checked_at row)})
          (store/list-products ds)))))

(defn get-product
  [ds]
  (fn [req]
    (let [id (parse-uuid (get-in req [:path-params :id]))]
      (if-not id
        (responses/error-response 400 "invalid_id" "Invalid product id.")
        (if-let [product (store/get-product ds id)]
          (responses/json-response
           {:id (:id product)
            :url (:url product)
            :canonicalUrl (:canonical_url product)
            :domain (:domain product)
            :title (:title product)
            :currency (:currency product)
            :createdAt (:created_at product)
            :updatedAt (:updated_at product)
            :lastCheckedAt (:last_checked_at product)})
          (responses/error-response 404 "not_found" "Product not found."))))))

(defn get-price-history
  [ds]
  (fn [req]
    (let [id (parse-uuid (get-in req [:path-params :id]))
          {:keys [from to]} (:query-params req)
          from-instant (when (seq from)
                         (try (Instant/parse from)
                              (catch Exception _ nil)))
          to-instant (when (seq to)
                       (try (Instant/parse to)
                            (catch Exception _ nil)))]
      (cond
        (nil? id)
        (responses/error-response 400 "invalid_id" "Invalid product id.")

        (and (seq from) (nil? from-instant))
        (responses/error-response 400 "invalid_range" "Invalid 'from' timestamp.")

        (and (seq to) (nil? to-instant))
        (responses/error-response 400 "invalid_range" "Invalid 'to' timestamp.")

        :else
        (responses/json-response
         {:productId id
          :points (map (fn [row]
                         {:t (:checked_at row)
                          :price (:price row)
                          :currency (:currency row)})
                       (store/price-history ds {:product-id id
                                                :from from-instant
                                                :to to-instant}))})))))

(defn delete-product
  [ds]
  (fn [req]
    (let [id (parse-uuid (get-in req [:path-params :id]))
          deleted (when id (store/delete-product! ds id))]
      (cond
        (nil? id)
        (responses/error-response 400 "invalid_id" "Invalid product id.")

        (nil? deleted)
        (responses/error-response 404 "not_found" "Product not found.")

        :else
        (responses/no-content)))))

(defn refresh-product
  [_]
  (fn [_]
    (responses/error-response 501 "not_implemented" "Refresh is not implemented yet.")))
