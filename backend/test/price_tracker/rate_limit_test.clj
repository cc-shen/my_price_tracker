(ns price-tracker.rate-limit-test
  (:require [clojure.test :refer [deftest is]]
            [price-tracker.rate-limit :as rate-limit]
            [price-tracker.responses :as responses]))

(deftest rate-limit-enforced
  (let [handler (rate-limit/wrap-rate-limit (fn [_] (responses/json-response {:ok true}))
                                            {:limit-per-minute 2})
        req {:uri "/api/products"
             :remote-addr "127.0.0.1"}]
    (is (= 200 (:status (handler req))))
    (is (= 200 (:status (handler req))))
    (is (= 429 (:status (handler req))))))
