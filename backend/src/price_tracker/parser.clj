(ns price-tracker.parser
  (:require [cheshire.core :as json]
            [clojure.string :as str])
  (:import [org.jsoup Jsoup]))

(def ^:private price-number-pattern #"\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?")

(def ^:private currency-token->code
  {"USD" "USD"
   "US$" "USD"
   "CAD" "CAD"
   "C$" "CAD"
   "CA$" "CAD"
   "EUR" "EUR"
   "GBP" "GBP"
   "JPY" "JPY"
   "€" "EUR"
   "£" "GBP"
   "¥" "JPY"})

(def default-registry
  {:exact {}
   :suffix {"amazon.com" {:name "amazon"
                          :parser-version "amazon-v1"
                          :title-selectors ["#productTitle"
                                            "h1#title span#productTitle"
                                            "meta[name=title]"]
                          :price-selectors ["#priceblock_ourprice"
                                            "#priceblock_dealprice"
                                            "span.a-price span.a-offscreen"
                                            "meta[property=product:price:amount]"]}
            "amazon.ca" {:name "amazon"
                         :parser-version "amazon-v1"
                         :title-selectors ["#productTitle"
                                           "h1#title span#productTitle"
                                           "meta[name=title]"]
                         :price-selectors ["#priceblock_ourprice"
                                           "#priceblock_dealprice"
                                           "span.a-price span.a-offscreen"
                                           "meta[property=product:price:amount]"]}
            "lululemon.com" {:name "lululemon"
                             :parser-version "lululemon-v1"
                             :title-selectors ["h1[data-testid=pdp-product-name]"
                                               "h1"]
                             :price-selectors ["span[data-testid=pdp-price]"
                                               "div[data-testid=pdp-price]"
                                               "meta[property=product:price:amount]"]}}
   :default {:name "generic"
             :parser-version "v1"
             :title-selectors []
             :price-selectors []}})

(defn- normalize-host
  [host]
  (some-> host str/trim str/lower-case))

(defn resolve-parser
  [registry host]
  (let [host (normalize-host host)]
    (or (get-in registry [:exact host])
        (some (fn [[suffix parser]]
                (when (and host
                           (or (= host suffix)
                               (str/ends-with? host (str "." suffix))))
                  parser))
              (sort-by (comp count key) > (:suffix registry)))
        (:default registry))))

(defn- element->text
  [element]
  (when element
    (let [tag (.tagName element)
          raw (if (= "meta" tag)
                (.attr element "content")
                (.text element))]
      (some-> raw str/trim (not-empty)))))

(defn- select-first-text
  [document selectors]
  (some (fn [selector]
          (element->text (.first (.select document selector))))
        selectors))

(defn- parse-number
  [value]
  (when (seq value)
    (let [cleaned (-> value
                      (str/replace #"," "")
                      (str/replace #"\s" ""))]
      (try
        (bigdec cleaned)
        (catch NumberFormatException _
          nil)))))

(defn- detect-currency
  [value]
  (when (seq value)
    (let [token (some (fn [candidate]
                        (when (str/includes? value candidate)
                          candidate))
                      (keys currency-token->code))]
      (get currency-token->code token))))

(defn- parse-price-text
  [value]
  (when (seq value)
    (let [number-match (re-find price-number-pattern value)
          currency (detect-currency value)
          price (parse-number number-match)]
      (when price
        {:price price
         :currency currency
         :raw-price-text value}))))

(defn- parse-dom
  [document parser]
  (let [title (select-first-text document (:title-selectors parser))
        price-text (select-first-text document (:price-selectors parser))
        price (parse-price-text price-text)]
    (when (or title price)
      (merge {:title title}
             price
             {:parser-source "dom"
              :parser-version (:parser-version parser)}))))

(defn- product-type?
  [value]
  (let [value (cond
                (string? value) [value]
                (sequential? value) value
                :else [])]
    (some (fn [entry]
            (let [entry (str/lower-case (str entry))]
              (or (= entry "product")
                  (str/ends-with? entry ":product"))))
          value)))

(defn- find-product-node
  [node]
  (cond
    (and (map? node) (product-type? (get node "@type")))
    node

    (map? node)
    (or (find-product-node (get node "@graph"))
        (some find-product-node (vals node)))

    (sequential? node)
    (some find-product-node node)

    :else nil))

(defn- parse-json-ld-node
  [node]
  (when-let [product (find-product-node node)]
    (let [title (or (get product "name")
                    (get product :name))
          offers (or (get product "offers")
                     (get product :offers))
          offer (cond
                  (map? offers) offers
                  (sequential? offers) (first offers)
                  :else nil)
          price (or (get offer "price")
                    (get offer :price))
          currency (or (get offer "priceCurrency")
                       (get offer :priceCurrency))
          availability (or (get offer "availability")
                           (get offer :availability))
          price-text (when (and price (string? price)) price)
          parsed-price (if price-text
                         (parse-price-text price-text)
                         {:price (parse-number (str price))})]
      (when (or title (:price parsed-price))
        (merge {:title (when (seq title) title)
                :currency currency
                :availability availability
                :parser-source "json-ld"
                :parser-version "json-ld-v1"}
               parsed-price
               (when price-text {:raw-price-text price-text}))))))

(defn- parse-json-ld
  [document]
  (let [elements (.select document "script[type=application/ld+json]")]
    (some (fn [element]
            (let [raw (some-> (.data element) str/trim)]
              (when (seq raw)
                (try
                  (parse-json-ld-node (json/parse-string raw))
                  (catch Exception _ nil)))))
          elements)))

(defn- select-meta-content
  [document selector]
  (some-> (.first (.select document selector)) (element->text)))

(defn- parse-open-graph
  [document]
  (let [title (select-meta-content document "meta[property=og:title]")
        price-text (or (select-meta-content document "meta[property=product:price:amount]")
                       (select-meta-content document "meta[property=og:price:amount]"))
        currency (or (select-meta-content document "meta[property=product:price:currency]")
                     (select-meta-content document "meta[property=og:price:currency]"))
        parsed-price (parse-price-text price-text)]
    (when (or title parsed-price)
      (merge {:title title
              :currency (or currency (:currency parsed-price))
              :parser-source "open-graph"
              :parser-version "open-graph-v1"}
             parsed-price))))

(defn- parse-regex
  [document]
  (let [text (.text document)
        match (re-find #"(USD|US\$|CAD|C\$|CA\$|EUR|GBP|JPY|€|£|¥|\$)\s*\d{1,3}(?:[\d,\s]*)(?:\.\d{1,2})?" text)
        parsed (parse-price-text match)]
    (when parsed
      (merge parsed
             {:parser-source "regex"
              :parser-version "regex-v1"}))))

(defn- pick-first
  [results predicate]
  (some (fn [result]
          (when (predicate result) result))
        results))

(defn parse-product
  [registry host html]
  (let [document (Jsoup/parse html)
        parser (resolve-parser registry host)
        dom-result (when parser (parse-dom document parser))
        json-ld-result (parse-json-ld document)
        og-result (parse-open-graph document)
        regex-result (parse-regex document)
        results [dom-result json-ld-result og-result regex-result]
        base (pick-first results :price)
        title-source (pick-first results :title)
        combined (merge (or base {}) (or title-source {}))]
    (when (seq combined)
      (if (and base (nil? (:title base)) (:title title-source))
        (assoc combined :title (:title title-source))
        combined))))
