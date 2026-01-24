(ns price-tracker.fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [price-tracker.fetch :as fetch]))

(deftest validate-url
  (testing "accepts http/https URLs"
    (is (:ok (fetch/validate-url {:fetch {}} "https://example.com/product"))))

  (testing "honors domain allowlist"
    (is (:ok (fetch/validate-url {:fetch {:allowed-domains ["example.com"]}}
                                 "https://www.example.com/product")))
    (is (= "Domain is not allowed."
           (get-in (fetch/validate-url {:fetch {:allowed-domains ["example.com"]}}
                                       "https://not-example.com/product")
                   [:error :message]))))

  (testing "rejects localhost and private addresses"
    (is (= "Localhost URLs are not allowed."
           (get-in (fetch/validate-url {:fetch {}} "http://localhost/test")
                   [:error :message])))
    (is (= "Private or local addresses are not allowed."
           (get-in (fetch/validate-url {:fetch {}} "http://127.0.0.1/test")
                   [:error :message])))))

(deftest normalize-url-removes-tracking
  (is (= "https://example.com/product?color=red"
         (fetch/normalize-url "https://example.com/product?utm_source=x&color=red&fbclid=abc")))
  (is (= "https://example.com/"
         (fetch/normalize-url "https://example.com#section"))))

(deftest load-denylist-yaml
  (let [file (java.io.File/createTempFile "denylist" ".yml")]
    (try
      (spit file "denylist:\n  - amazon.ca\n  - Example.com\n")
      (let [config {:fetch {:denylist-path (.getAbsolutePath file)}}
            denylist (fetch/load-denylist config)]
        (is (= ["amazon.ca" "example.com"] denylist)))
      (finally
        (.delete file)))))
