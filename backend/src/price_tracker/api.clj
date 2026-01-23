(ns price-tracker.api
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [price-tracker.fetch :as fetch]
            [price-tracker.responses :as responses]
            [price-tracker.store :as store])
  (:import [java.time Instant]
           [org.postgresql.util PSQLException]))

(def ^:private manual-price-pattern
  #"\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?")

(defn- parse-manual-price
  [value]
  (cond
    (nil? value) nil
    (number? value) (try (bigdec value) (catch Exception _ nil))
    (string? value) (let [match (re-find manual-price-pattern value)
                          cleaned (when match
                                    (-> match
                                        (str/replace #"," "")
                                        (str/replace #"\s" "")))]
                      (when (seq cleaned)
                        (try (bigdec cleaned) (catch Exception _ nil))))
    :else nil))

(defn- normalize-manual-currency
  [value]
  (when (string? value)
    (let [trimmed (str/upper-case (str/trim value))]
      (when (seq trimmed)
        (when (re-matches #"[A-Z]{3}" trimmed)
          trimmed)))))

(defn- parse-manual-entry
  [manual]
  (if (and manual (not (map? manual)))
    {:error {:type "validation_error" :message "Manual entry must be an object."}}
    (let [title (when (string? (:title manual))
                  (str/trim (:title manual)))
          price (parse-manual-price (:price manual))
          currency-raw (:currency manual)
          currency (normalize-manual-currency currency-raw)
          currency-provided? (and (string? currency-raw)
                                  (seq (str/trim currency-raw)))]
      (cond
        (str/blank? (or title ""))
        {:error {:type "validation_error" :message "Manual entry requires a title."}}

        (nil? price)
        {:error {:type "validation_error" :message "Manual entry requires a valid price."}}

        (and currency-provided? (nil? currency))
        {:error {:type "validation_error" :message "Currency must be a 3-letter code (e.g., USD)."}}

        :else
        {:ok {:title title
              :price price
              :currency currency}}))))

(defn- parse-refresh-input
  [body]
  (if (nil? body)
    {:error {:type "invalid_json" :message "Request body must be JSON."}}
    (let [price (parse-manual-price (:price body))
          currency-raw (:currency body)
          currency (normalize-manual-currency currency-raw)
          currency-provided? (and (string? currency-raw)
                                  (seq (str/trim currency-raw)))]
      (cond
        (nil? price)
        {:error {:type "validation_error" :message "Price is required."}}

        (and currency-provided? (nil? currency))
        {:error {:type "validation_error" :message "Currency must be a 3-letter code (e.g., USD)."}}

        :else
        {:ok {:price price
              :currency currency}}))))

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

(defn- create-product-input
  [config body]
  (let [{:keys [url manual]} body
        manual-input (parse-manual-entry manual)]
    (cond
      (nil? body)
      {:error {:type "invalid_json" :message "Request body must be JSON."}}

      (str/blank? url)
      {:error {:type "validation_error" :message "URL is required."}}

      (nil? manual)
      {:error {:type "validation_error" :message "Manual entry is required."}}

      (:error manual-input)
      manual-input

      :else
      (let [{:keys [ok error]} (fetch/validate-url config url)
            normalized (fetch/normalize-url url)]
        (cond
          error {:error error}
          (nil? normalized) {:error {:type "validation_error" :message "Invalid URL."}}
          :else {:ok {:url normalized
                      :canonical-url normalized
                      :domain (:host ok)
                      :manual (:ok manual-input)}})))))

(defn create-product
  [ds config]
  (fn [req]
    (let [{:keys [ok error]} (create-product-input config (:json-body req))]
      (if error
        (responses/error-response 422 (:type error) (:message error))
        (let [manual (:manual ok)]
          (try
            (with-transaction
              ds
              (fn [tx]
                (let [checked-at (Instant/now)
                      product (store/create-product! tx {:url (:url ok)
                                                         :canonical-url (:canonical-url ok)
                                                         :domain (:domain ok)
                                                         :title (:title manual)
                                                         :currency (:currency manual)})
                      snapshot (store/insert-price-snapshot! tx
                                                             {:product-id (:id product)
                                                              :price (:price manual)
                                                              :currency (:currency manual)
                                                              :source "manual-entry"
                                                              :parser-version "manual-v1"
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
                (throw e)))))))))

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
  [ds _config]
  (fn [req]
    (let [id (parse-uuid (get-in req [:path-params :id]))]
      (cond
        (nil? id)
        (responses/error-response 400 "invalid_id" "Invalid product id.")

        :else
        (if-let [product (store/get-product ds id)]
          (let [{:keys [ok error]} (parse-refresh-input (:json-body req))]
            (if error
              (responses/error-response 422 (:type error) (:message error))
              (with-transaction
                ds
                (fn [tx]
                  (let [checked-at (Instant/now)
                        currency (or (:currency ok) (:currency product))
                        snapshot (store/insert-price-snapshot! tx
                                                               {:product-id (:id product)
                                                                :price (:price ok)
                                                                :currency currency
                                                                :source "manual-refresh"
                                                                :parser-version "manual-v1"
                                                                :checked-at checked-at})]
                    (store/update-last-checked! tx {:product-id (:id product)
                                                    :checked-at checked-at})
                    (responses/json-response
                     {:id (:id product)
                      :price (:price snapshot)
                      :currency (:currency snapshot)
                      :lastCheckedAt (:checked_at snapshot)}))))))
          (responses/error-response 404 "not_found" "Product not found."))))))
