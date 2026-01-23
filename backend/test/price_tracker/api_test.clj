(ns price-tracker.api-test
  (:require [cheshire.core :as json]
            [cheshire.parse]
            [clojure.test :refer [deftest is testing]]
            [price-tracker.api :as api]
            [price-tracker.fetch :as fetch]
            [price-tracker.store :as store]))

(defn- parse-body
  [resp]
  (when-let [body (:body resp)]
    (let [raw (if (string? body) body (slurp body))]
      (binding [cheshire.parse/*use-bigdecimals?* true]
        (json/parse-string raw true)))))

(deftest create-product-validation
  (let [config {:fetch {}}
        handler (api/create-product nil config)]
    (testing "rejects missing JSON"
      (let [resp (handler {:json-body nil})]
        (is (= 422 (:status resp)))
        (is (= "invalid_json" (get-in (parse-body resp) [:error :type])))))

    (testing "rejects blank URL"
      (let [resp (handler {:json-body {:url ""}})]
        (is (= 422 (:status resp)))
        (is (= "validation_error" (get-in (parse-body resp) [:error :type])))))

    (testing "rejects invalid URL"
      (with-redefs [fetch/validate-url (fn [_ _] {:error {:type "validation_error" :message "Invalid URL."}})
                    fetch/normalize-url (fn [_] nil)]
        (let [resp (handler {:json-body {:url "not-a-url"
                                         :manual {:title "Manual Item"
                                                  :price 12.50}}})]
          (is (= 422 (:status resp)))
          (is (= "validation_error" (get-in (parse-body resp) [:error :type]))))))

    (testing "rejects missing manual entry"
      (with-redefs [fetch/validate-url (fn [_ _] {:ok {:host "example.com" :uri nil}})
                    fetch/normalize-url (fn [_] "https://example.com/item")]
        (let [resp (handler {:json-body {:url "https://example.com/item"}})]
          (is (= 422 (:status resp)))
          (is (= "validation_error" (get-in (parse-body resp) [:error :type]))))))))

(deftest create-product-success
  (let [config {:fetch {}}
        handler (api/create-product nil config)]
    (with-redefs [fetch/validate-url (fn [_ _] {:ok {:host "example.com" :uri nil}})
                  fetch/normalize-url (fn [_] "https://example.com/item")
                  price-tracker.api/with-transaction (fn [_ f] (f :tx))
                  store/create-product! (fn [_ _] {:id "p1"
                                                   :title "Manual Item"
                                                   :domain "example.com"})
                  store/insert-price-snapshot! (fn [_ _] {:price 12.50M
                                                          :currency "USD"
                                                          :checked_at "now"})
                  store/update-last-checked! (fn [_ _] nil)]
      (let [resp (handler {:json-body {:url "https://example.com/item"
                                       :manual {:title "Manual Item"
                                                :price 12.50
                                                :currency "USD"}}})
            body (parse-body resp)]
        (is (= 201 (:status resp)))
        (is (= "p1" (:id body)))
        (is (= "Manual Item" (:title body)))
        (is (== 12.50M (:price body)))))))

(deftest create-product-conflict
  (let [config {:fetch {}}
        handler (api/create-product nil config)]
    (with-redefs [fetch/validate-url (fn [_ _] {:ok {:host "example.com" :uri nil}})
                  fetch/normalize-url (fn [_] "https://example.com/item")
                  price-tracker.api/with-transaction (fn [_ f] (f :tx))
                  store/create-product! (fn [_ _]
                                          (throw (org.postgresql.util.PSQLException.
                                                  "dup"
                                                  org.postgresql.util.PSQLState/UNIQUE_VIOLATION)))]
      (let [resp (handler {:json-body {:url "https://example.com/item"
                                       :manual {:title "Manual Item"
                                                :price 12.50}}})]
        (is (= 409 (:status resp)))
        (is (= "already_exists" (get-in (parse-body resp) [:error :type])))))))

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
      (is (= 10.0M (-> body first :currentPrice))))))

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

(deftest refresh-product-not-found
  (with-redefs [store/get-product (fn [_ _] nil)]
    (let [handler (api/refresh-product nil {})
          resp (handler {:path-params {:id "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}})]
      (is (= 404 (:status resp)))
      (is (= "not_found" (get-in (parse-body resp) [:error :type]))))))
