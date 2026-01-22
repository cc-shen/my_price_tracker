(ns price-tracker.api
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [price-tracker.fetch :as fetch]
            [price-tracker.parser :as parser]
            [price-tracker.responses :as responses]
            [price-tracker.store :as store])
  (:import [java.time Instant]
           [org.postgresql.util PSQLException]))

(defn- sql-state
  [^Exception e]
  (loop [ex e]
    (cond
      (nil? ex) nil
      (instance? PSQLException ex) (.getSQLState ^PSQLException ex)
      :else (recur (.getCause ex)))))

(defn- with-transaction
  [ds f]
  (jdbc/with-transaction [tx ds]
    (f tx)))

(defn- fetch-error-response
  [error]
  (case (:type error)
    "validation_error" (responses/error-response 422 "validation_error" (:message error))
    "fetch_error" (responses/error-response 502 "fetch_error" (:message error))
    (responses/error-response 500 "internal_error" "Unexpected error.")))

(defn- parse-product-error
  [parsed {:keys [require-title] :or {require-title true}}]
  (cond
    (nil? parsed)
    {:type "parse_error" :message "Unable to parse product metadata."}

    (nil? (:price parsed))
    {:type "parse_error" :message "Unable to parse product price."}

    (and require-title (str/blank? (:title parsed)))
    {:type "parse_error" :message "Unable to parse product title."}

    :else nil))

(defn- create-product-input
  [config body]
  (let [{:keys [url]} body]
    (cond
      (nil? body)
      {:error {:type "invalid_json" :message "Request body must be JSON."}}

      (str/blank? url)
      {:error {:type "validation_error" :message "URL is required."}}

      :else
      (let [{:keys [ok error]} (fetch/validate-url config url)
            normalized (fetch/normalize-url url)]
        (cond
          error {:error error}
          (nil? normalized) {:error {:type "validation_error" :message "Invalid URL."}}
          :else {:ok {:url normalized
                      :canonical-url normalized
                      :domain (:host ok)
                      :fetch-url normalized}})))))

(defn create-product
  [ds config]
  (fn [req]
    (let [{:keys [ok error]} (create-product-input config (:json-body req))]
      (if error
        (responses/error-response 422 (:type error) (:message error))
        (let [{:keys [error fetch-result]} (let [result (fetch/fetch-html config (:fetch-url ok))]
                                             {:error (:error result) :fetch-result (:ok result)})]
          (if error
            (fetch-error-response error)
            (let [parsed (parser/parse-product parser/default-registry (:domain ok) (:body fetch-result))
                  parse-error (parse-product-error parsed {:require-title true})]
              (if parse-error
                (responses/error-response 422 (:type parse-error) (:message parse-error))
                (try
                  (with-transaction
                    ds
                    (fn [tx]
                      (let [checked-at (Instant/now)
                            product (store/create-product! tx {:url (:url ok)
                                                               :canonical-url (:canonical-url ok)
                                                               :domain (:domain ok)
                                                               :title (:title parsed)
                                                               :currency (:currency parsed)})
                            snapshot (store/insert-price-snapshot! tx
                                                                   {:product-id (:id product)
                                                                    :price (:price parsed)
                                                                    :currency (:currency parsed)
                                                                    :source "manual"
                                                                    :raw-price-text (:raw-price-text parsed)
                                                                    :parser-version (:parser-version parsed)
                                                                    :availability (:availability parsed)
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
                          :lastCheckedAt (:checked_at snapshot)}))))
                  (catch Exception e
                    (if (= "23505" (sql-state e))
                      (responses/error-response 409 "already_exists" "Product already exists.")
                      (throw e))))))))))))

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
  [ds config]
  (fn [req]
    (let [id (parse-uuid (get-in req [:path-params :id]))]
      (cond
        (nil? id)
        (responses/error-response 400 "invalid_id" "Invalid product id.")

        :else
        (if-let [product (store/get-product ds id)]
          (let [target-url (or (:canonical_url product) (:url product))
                {:keys [error]} (fetch/validate-url config target-url)]
            (cond
              error (fetch-error-response error)
              :else
              (let [normalized (or (fetch/normalize-url target-url) target-url)
                    {:keys [error fetch-result]} (let [result (fetch/fetch-html config normalized)]
                                                   {:error (:error result) :fetch-result (:ok result)})]
                (if error
                  (fetch-error-response error)
                  (let [parsed (parser/parse-product parser/default-registry (:domain product) (:body fetch-result))
                        parse-error (parse-product-error parsed {:require-title false})]
                    (if parse-error
                      (responses/error-response 422 (:type parse-error) (:message parse-error))
                      (with-transaction
                        ds
                        (fn [tx]
                          (let [checked-at (Instant/now)
                                snapshot (store/insert-price-snapshot! tx
                                                                       {:product-id (:id product)
                                                                        :price (:price parsed)
                                                                        :currency (:currency parsed)
                                                                        :source "manual"
                                                                        :raw-price-text (:raw-price-text parsed)
                                                                        :parser-version (:parser-version parsed)
                                                                        :availability (:availability parsed)
                                                                        :checked-at checked-at})]
                            (store/update-last-checked! tx {:product-id (:id product)
                                                            :checked-at checked-at})
                            (responses/json-response
                             {:id (:id product)
                              :price (:price snapshot)
                              :currency (:currency snapshot)
                              :lastCheckedAt (:checked_at snapshot)}))))))))))
          (responses/error-response 404 "not_found" "Product not found."))))))
