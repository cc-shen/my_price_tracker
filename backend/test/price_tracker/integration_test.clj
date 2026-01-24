(ns price-tracker.integration-test
  (:require [cheshire.core :as json]
            [cheshire.parse]
            [clojure.test :refer [deftest is use-fixtures]]
            [price-tracker.api :as api]
            [price-tracker.db :as db]
            [price-tracker.fetch :as fetch]
            [price-tracker.store :as store])
  (:import [java.time Instant]))

(def ^:dynamic *ds* nil)

(defn- db-fixture
  [f]
  (if-let [url (System/getenv "DATABASE_URL")]
    (let [ds (db/datasource url)]
      (binding [*ds* ds]
        (db/execute ds ["TRUNCATE price_snapshots, products"])
        (try
          (f)
          (finally
            (db/close-datasource ds)))))
    (f)))

(use-fixtures :each db-fixture)

(defn- parse-body
  [resp]
  (when-let [body (:body resp)]
    (let [raw (if (string? body) body (slurp body))]
      (binding [cheshire.parse/*use-bigdecimals?* true]
        (json/parse-string raw true)))))

(defn- with-db
  [f]
  (if *ds*
    (f)
    (is true)))

(defn- count-for-product
  [table product-id]
  (-> (db/execute-one *ds*
                      [(str "SELECT COUNT(*) AS count FROM " table " WHERE product_id = ?")
                       product-id])
      :count))

(deftest create-product-persists-product-and-snapshot
  (with-db
    (fn []
      (with-redefs [fetch/validate-url (fn [_ _] {:ok {:host "example.com" :uri nil}})
                    fetch/normalize-url (fn [_] "https://example.com/item")]
        (let [handler (api/create-product *ds* {:fetch {}})
              resp (handler {:json-body {:url "https://example.com/item"
                                         :manual {:title "Manual Item"
                                                  :price 12.50
                                                  :currency "USD"}}})
              body (parse-body resp)
              product-id (java.util.UUID/fromString (:id body))
              product (store/get-product *ds* product-id)]
          (is (= 201 (:status resp)))
          (is (= "Manual Item" (:title product)))
          (is (= 1 (count-for-product "price_snapshots" product-id))))))))

(deftest list-products-returns-latest-snapshot
  (with-db
    (fn []
      (let [product (store/create-product! *ds* {:url "https://example.com/item"
                                                 :canonical-url "https://example.com/item"
                                                 :domain "example.com"
                                                 :title "Item"
                                                 :currency "USD"})
            t1 (Instant/parse "2024-01-01T00:00:00Z")
            t2 (Instant/parse "2024-02-01T00:00:00Z")]
        (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                            :price 10.00M
                                            :currency "USD"
                                            :source "manual"
                                            :checked-at t1})
        (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                            :price 8.00M
                                            :currency "USD"
                                            :source "manual"
                                            :checked-at t2})
        (store/update-last-checked! *ds* {:product-id (:id product)
                                          :checked-at t2})
        (let [handler (api/list-products *ds*)
              resp (handler {})
              body (parse-body resp)]
          (is (= 200 (:status resp)))
          (is (= 8.00M (-> body first :currentPrice))))))))

(deftest insert-price-snapshot-upserts-by-day
  (with-db
    (fn []
      (let [product (store/create-product! *ds* {:url "https://example.com/item"
                                                 :canonical-url "https://example.com/item"
                                                 :domain "example.com"
                                                 :title "Item"
                                                 :currency "USD"})
            zone (java.time.ZoneId/systemDefault)
            t1 (-> (java.time.ZonedDateTime/of 2024 1 1 10 0 0 0 zone)
                   (.toInstant))
            t2 (-> (java.time.ZonedDateTime/of 2024 1 1 15 0 0 0 zone)
                   (.toInstant))]
        (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                            :price 10.00M
                                            :currency "USD"
                                            :source "manual"
                                            :checked-at t1})
        (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                            :price 9.00M
                                            :currency "USD"
                                            :source "manual"
                                            :checked-at t2})
        (is (= 1 (count-for-product "price_snapshots" (:id product))))
        (let [row (db/execute-one *ds*
                                  ["SELECT price FROM price_snapshots WHERE product_id = ?"
                                   (:id product)])]
          (is (= 9.00M (:price row))))))))

(deftest get-price-history-filters-range
  (with-db
    (fn []
      (let [product (store/create-product! *ds* {:url "https://example.com/item"
                                                 :canonical-url "https://example.com/item"
                                                 :domain "example.com"
                                                 :title "Item"
                                                 :currency "USD"})
            t1 (Instant/parse "2024-01-01T00:00:00Z")
            t2 (Instant/parse "2024-02-01T00:00:00Z")
            t3 (Instant/parse "2024-03-01T00:00:00Z")]
        (doseq [[price checked-at] [[10.00M t1] [9.00M t2] [8.00M t3]]]
          (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                              :price price
                                              :currency "USD"
                                              :source "manual"
                                              :checked-at checked-at}))
        (let [handler (api/get-price-history *ds*)
              resp (handler {:path-params {:id (str (:id product))}
                             :query-params {:from "2024-02-01T00:00:00Z"
                                            :to "2024-03-01T00:00:00Z"}})
              body (parse-body resp)]
          (is (= 200 (:status resp)))
          (is (= 2 (count (:points body))))
          (is (= [9.00M 8.00M] (map :price (:points body)))))))))

(deftest refresh-product-appends-snapshot
  (with-db
    (fn []
      (let [product (store/create-product! *ds* {:url "https://example.com/item"
                                                 :canonical-url "https://example.com/item"
                                                 :domain "example.com"
                                                 :title "Item"
                                                 :currency "USD"})
            t1 (Instant/parse "2024-01-01T00:00:00Z")]
        (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                            :price 10.00M
                                            :currency "USD"
                                            :source "manual"
                                            :checked-at t1})
        (let [handler (api/refresh-product *ds* {})
              resp (handler {:path-params {:id (str (:id product))}
                             :json-body {:price 9.50
                                         :currency "USD"}})]
          (is (= 200 (:status resp)))
          (is (= 2 (count-for-product "price_snapshots" (:id product)))))))))

(deftest delete-product-cascades-snapshots
  (with-db
    (fn []
      (let [product (store/create-product! *ds* {:url "https://example.com/item"
                                                 :canonical-url "https://example.com/item"
                                                 :domain "example.com"
                                                 :title "Item"
                                                 :currency "USD"})
            t1 (Instant/parse "2024-01-01T00:00:00Z")]
        (store/insert-price-snapshot! *ds* {:product-id (:id product)
                                            :price 10.00M
                                            :currency "USD"
                                            :source "manual"
                                            :checked-at t1})
        (let [handler (api/delete-product *ds*)
              resp (handler {:path-params {:id (str (:id product))}})]
          (is (= 204 (:status resp)))
          (is (nil? (store/get-product *ds* (:id product))))
          (is (= 0 (count-for-product "price_snapshots" (:id product)))))))))
