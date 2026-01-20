(ns price-tracker.parser-test
  (:require [clojure.test :refer :all]
            [price-tracker.parser :as parser]))

(deftest resolve-parser-prefers-exact-then-suffix
  (let [registry {:exact {"shop.example.com" {:name "exact"}}
                  :suffix {"example.com" {:name "suffix"}}
                  :default {:name "default"}}]
    (is (= "exact" (:name (parser/resolve-parser registry "shop.example.com"))))
    (is (= "suffix" (:name (parser/resolve-parser registry "news.example.com"))))
    (is (= "default" (:name (parser/resolve-parser registry "other.com"))))))

(deftest parse-product-prefers-json-ld
  (let [html "<html><head><script type=\"application/ld+json\">{\"@context\":\"http://schema.org\",\"@type\":\"Product\",\"name\":\"Test Item\",\"offers\":{\"price\":\"19.99\",\"priceCurrency\":\"USD\",\"availability\":\"http://schema.org/InStock\"}}</script></head></html>"
        result (parser/parse-product parser/default-registry "example.com" html)]
    (is (= "Test Item" (:title result)))
    (is (= "USD" (:currency result)))
    (is (= "http://schema.org/InStock" (:availability result)))
    (is (= 19.99M (:price result)))
    (is (= "json-ld" (:parser-source result)))))

(deftest parse-product-falls-back-to-open-graph
  (let [html (str "<html><head>"
                  "<meta property=\"og:title\" content=\"OG Item\"/>"
                  "<meta property=\"product:price:amount\" content=\"25.50\"/>"
                  "<meta property=\"product:price:currency\" content=\"USD\"/>"
                  "</head></html>")
        result (parser/parse-product parser/default-registry "example.com" html)]
    (is (= "OG Item" (:title result)))
    (is (= 25.50M (:price result)))
    (is (= "USD" (:currency result)))
    (is (= "open-graph" (:parser-source result)))))

(deftest parse-product-falls-back-to-regex
  (let [html "<html><body><p>Limited offer: now $42.00 for today only.</p></body></html>"
        result (parser/parse-product parser/default-registry "example.com" html)]
    (is (= 42.00M (:price result)))
    (is (= "regex" (:parser-source result)))))
