(ns price-tracker.http-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [price-tracker.http :as http]
            [ring.mock.request :as mock]))

(defn- parse-body
  [resp]
  (when-let [body (:body resp)]
    (let [raw (if (string? body) body (slurp body))]
      (json/parse-string raw true))))

(deftest invalid-json-body
  (let [handler (http/handler {:cors {}} nil)
        request (-> (mock/request :post "/api/products")
                    (mock/content-type "application/json")
                    (mock/body "{"))
        resp (handler request)]
    (is (= 400 (:status resp)))
    (is (= "invalid_json" (get-in (parse-body resp) [:error :type])))))
