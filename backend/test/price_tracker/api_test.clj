(ns price-tracker.api-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [price-tracker.api :as api]
            [price-tracker.store :as store]))

(defn- parse-body
  [resp]
  (when-let [body (:body resp)]
    (let [raw (if (string? body) body (slurp body))]
      (json/parse-string raw true))))

(deftest create-product-validation
  (let [handler (api/create-product nil)]
    (testing "rejects missing JSON"
      (let [resp (handler {:json-body nil})]
        (is (= 422 (:status resp)))
        (is (= "invalid_json" (get-in (parse-body resp) [:error :type])))))

    (testing "rejects invalid URL"
      (let [resp (handler {:json-body {:url "not-a-url" :title "Item" :price 12.5}})]
        (is (= 422 (:status resp)))
        (is (= "validation_error" (get-in (parse-body resp) [:error :type])))))

    (testing "rejects missing title"
      (let [resp (handler {:json-body {:url "https://example.com" :title "" :price 12.5}})]
        (is (= 422 (:status resp)))
        (is (= "validation_error" (get-in (parse-body resp) [:error :type])))))

    (testing "rejects invalid price"
      (let [resp (handler {:json-body {:url "https://example.com" :title "Item" :price "nope"}})]
        (is (= 422 (:status resp)))
        (is (= "validation_error" (get-in (parse-body resp) [:error :type])))))))

(deftest list-products-mapping
  (with-redefs [store/list-products (fn [_]
                                      [{:id "1"
                                        :title "Item"
                                        :domain "example.com"
                                        :currency "USD"
                                        :current_price 10.0
                                        :current_currency "USD"
                                        :last_checked_at nil}])]
    (let [handler (api/list-products nil)
          resp (handler {})
          body (parse-body resp)]
      (is (= 200 (:status resp)))
      (is (= "Item" (-> body first :title)))
      (is (= 10.0 (-> body first :currentPrice))))))

(deftest get-product-invalid-id
  (let [handler (api/get-product nil)
        resp (handler {:path-params {:id "nope"}})]
    (is (= 400 (:status resp)))
    (is (= "invalid_id" (get-in (parse-body resp) [:error :type])))))

(deftest price-history-invalid-range
  (let [handler (api/get-price-history nil)
        resp (handler {:path-params {:id "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}
                       :query-params {:from "bad"}})]
    (is (= 400 (:status resp)))
    (is (= "invalid_range" (get-in (parse-body resp) [:error :type])))))

(deftest delete-product-not-found
  (with-redefs [store/delete-product! (fn [_ _] nil)]
    (let [handler (api/delete-product nil)
          resp (handler {:path-params {:id "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}})]
      (is (= 404 (:status resp)))
      (is (= "not_found" (get-in (parse-body resp) [:error :type]))))))
