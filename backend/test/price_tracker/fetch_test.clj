(ns price-tracker.fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [price-tracker.fetch :as fetch])
  (:import [java.net InetAddress]))

(defn- ipv4
  [a b c d]
  (InetAddress/getByAddress (byte-array [(unchecked-byte a)
                                         (unchecked-byte b)
                                         (unchecked-byte c)
                                         (unchecked-byte d)])))

(deftest validate-url
  (with-redefs [fetch/resolve-host (fn [_] [(ipv4 93 184 216 34)])]
    (testing "accepts http/https URLs"
      (is (:ok (fetch/validate-url {:fetch {}} "https://example.com/product"))))

    (testing "honors domain allowlist"
      (is (:ok (fetch/validate-url {:fetch {:allowed-domains ["example.com"]}}
                                   "https://www.example.com/product")))
      (is (= "Domain is not allowed."
             (get-in (fetch/validate-url {:fetch {:allowed-domains ["example.com"]}}
                                         "https://not-example.com/product")
                     [:error :message])))))

  (testing "rejects localhost and private addresses"
    (is (= "Localhost URLs are not allowed."
           (get-in (fetch/validate-url {:fetch {}} "http://localhost/test")
                   [:error :message])))
    (is (= "Private or local addresses are not allowed."
           (get-in (fetch/validate-url {:fetch {}} "http://127.0.0.1/test")
                   [:error :message]))))

  (testing "handles DNS failures"
    (with-redefs [fetch/resolve-host (fn [_] (throw (Exception. "no dns")))]
      (is (= "Unable to resolve host."
             (get-in (fetch/validate-url {:fetch {}} "https://example.com")
                     [:error :message]))))))

(deftest normalize-url-removes-tracking
  (is (= "https://example.com/product?color=red"
         (fetch/normalize-url "https://example.com/product?utm_source=x&color=red&fbclid=abc")))
  (is (= "https://example.com/"
         (fetch/normalize-url "https://example.com#section"))))
