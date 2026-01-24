(ns price-tracker.fetch
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as log])
  (:import [java.net IDN InetAddress URI URLDecoder URLEncoder]
           [org.jsoup Jsoup]))

(def ^:private max-url-length 2048)
(def ^:private tracking-params
  #{"utm_source" "utm_medium" "utm_campaign" "utm_term" "utm_content"
    "gclid" "fbclid" "mc_cid" "mc_eid" "ref" "ref_" "tag" "affid"
    "affiliate" "irclickid" "irgwc" "scid"})

(def ^:private default-fetch-timeout-ms 8000)
(def ^:private default-user-agent "PriceTrackerBot/0.1 (+local-only)")
(def ^:private rejected-statuses #{401 403 406 429 451})

(def ^:private price-pattern
  #"\d{1,3}(?:[,\s]\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?")

(def ^:private title-meta-selectors
  ["meta[property=og:title]"
   "meta[name=og:title]"
   "meta[name=twitter:title]"
   "meta[property=twitter:title]"
   "meta[name=title]"])

(def ^:private price-meta-selectors
  ["meta[property=product:price:amount]"
   "meta[name=product:price:amount]"
   "meta[property=product:price]"
   "meta[name=product:price]"
   "meta[property=og:price:amount]"
   "meta[name=og:price:amount]"
   "meta[property=og:price]"
   "meta[name=og:price]"
   "meta[itemprop=price]"
   "meta[name=price]"])

(def ^:private currency-meta-selectors
  ["meta[property=product:price:currency]"
   "meta[name=product:price:currency]"
   "meta[property=og:price:currency]"
   "meta[name=og:price:currency]"
   "meta[itemprop=priceCurrency]"])

(defn- normalize-host
  [host]
  (some-> host str/trim str/lower-case))

(defn- parse-url
  [value]
  (when (and (string? value)
             (<= (count value) max-url-length))
    (try
      (let [uri (URI. value)
            scheme (some-> (.getScheme uri) str/lower-case)
            host (normalize-host (.getHost uri))]
        (when (and host (#{"http" "https"} scheme))
          {:uri uri
           :host host
           :scheme scheme
           :user-info (.getUserInfo uri)}))
      (catch Exception _
        nil))))

(defn- decode-param
  [value]
  (when value
    (URLDecoder/decode value "UTF-8")))

(defn- encode-param
  [value]
  (URLEncoder/encode value "UTF-8"))

(defn- parse-query
  [query]
  (when (seq query)
    (->> (str/split query #"&")
         (keep (fn [entry]
                 (when (seq entry)
                   (let [[raw-k raw-v] (str/split entry #"=" 2)
                         key (decode-param raw-k)
                         value (decode-param raw-v)]
                     (when (seq key)
                       [key value]))))))))

(defn- build-query
  [params]
  (when (seq params)
    (->> params
         (map (fn [[k v]]
                (str (encode-param k)
                     (when (some? v)
                       (str "=" (encode-param v))))))
         (str/join "&"))))

(defn normalize-url
  [value]
  (when-let [{:keys [uri host scheme user-info]} (parse-url value)]
    (let [raw-query (.getRawQuery uri)
          params (parse-query raw-query)
          filtered (remove (fn [[k _]] (contains? tracking-params (str/lower-case k))) params)
          query (build-query filtered)
          path (let [path (.getRawPath uri)]
                 (if (seq path) path "/"))
          port (.getPort uri)
          normalized (URI. scheme user-info host port path query nil)]
      (.toASCIIString normalized))))

(defn- domain-match?
  [host domain]
  (let [host (normalize-host host)
        domain (normalize-host domain)]
    (when (and host domain)
      (or (= host domain)
          (str/ends-with? host (str "." domain))))))

(defn- strip-comment
  [line]
  (first (str/split line #"#" 2)))

(defn- parse-denylist-yaml
  [raw]
  (let [lines (str/split-lines raw)]
    (loop [remaining lines
           in-denylist? false
           seen-key? false
           acc []]
      (if (empty? remaining)
        acc
        (let [line (-> (first remaining) strip-comment str/trim)]
          (cond
            (str/blank? line)
            (recur (rest remaining) in-denylist? seen-key? acc)

            (re-matches #"(?i)^denylist\s*:\s*$" line)
            (recur (rest remaining) true true acc)

            (re-matches #"^\S.*:\s*$" line)
            (recur (rest remaining) false true acc)

            (re-matches #"^-\s*.+$" line)
            (let [value (-> line
                            (str/replace #"^-\s*" "")
                            str/trim)]
              (recur (rest remaining)
                     in-denylist?
                     seen-key?
                     (if (and (seq value) (or in-denylist? (not seen-key?)))
                       (conj acc value)
                       acc)))

            :else
            (recur (rest remaining) in-denylist? seen-key? acc)))))))

(defn load-denylist
  [config]
  (let [path (get-in config [:fetch :denylist-path])]
    (if (str/blank? (or path ""))
      []
      (let [file (io/file path)]
        (if-not (.exists file)
          (do
            (log/warn "Denylist config not found:" path)
            [])
          (try
            (->> (parse-denylist-yaml (slurp file))
                 (map normalize-host)
                 (remove str/blank?)
                 distinct
                 vec)
            (catch Exception e
              (log/warn e "Failed to load denylist config:" path)
              [])))))))

(defn denylisted-domain?
  [config host]
  (let [denylist (or (get-in config [:fetch :denylist])
                     (load-denylist config)
                     [])]
    (some #(domain-match? host %) denylist)))

(defn- normalize-currency
  [value]
  (when (string? value)
    (let [trimmed (str/upper-case (str/trim value))]
      (when (re-matches #"[A-Z]{3}" trimmed)
        trimmed))))

(defn- parse-price
  [value]
  (when (string? value)
    (let [match (re-find price-pattern value)
          cleaned (when match
                    (-> match
                        (str/replace #"," "")
                        (str/replace #"\s" "")))]
      (when (seq cleaned)
        (try (bigdec cleaned)
             (catch Exception _ nil))))))

(defn- select-meta-content
  [doc selectors]
  (some (fn [selector]
          (some-> doc
                  (.select selector)
                  (.first)
                  (.attr "content")
                  str/trim
                  (not-empty)))
        selectors))

(defn- select-element-text
  [doc selector]
  (some-> doc
          (.select selector)
          (.first)
          (.text)
          str/trim
          (not-empty)))

(defn- extract-title
  [doc]
  (or (select-meta-content doc title-meta-selectors)
      (some-> doc .title str/trim not-empty)))

(defn- extract-price-text
  [doc]
  (or (select-meta-content doc price-meta-selectors)
      (select-meta-content doc ["meta[itemprop=price]"])
      (select-element-text doc "[itemprop=price]")))

(defn- extract-currency
  [doc price-text]
  (or (some-> (select-meta-content doc currency-meta-selectors)
              normalize-currency)
      (some-> (select-element-text doc "[itemprop=priceCurrency]")
              normalize-currency)
      (when-let [text (some-> price-text str/upper-case)]
        (normalize-currency (re-find #"\b[A-Z]{3}\b" text)))))

(defn- fetch-html
  [config url]
  (try
    (let [timeout-ms (or (get-in config [:fetch :timeout-ms]) default-fetch-timeout-ms)
          user-agent (or (get-in config [:fetch :user-agent]) default-user-agent)
          response (-> (Jsoup/connect url)
                       (.userAgent user-agent)
                       (.timeout timeout-ms)
                       (.followRedirects true)
                       (.ignoreHttpErrors true)
                       (.ignoreContentType true)
                       (.execute))
          status (.statusCode response)
          body (.body response)]
      (cond
        (contains? rejected-statuses status)
        {:error {:type "fetch_rejected"
                 :message "Request rejected by the retailer."
                 :details {:status status}}}

        (>= status 400)
        {:error {:type "fetch_failed"
                 :message "Unable to fetch product page."
                 :details {:status status}}}

        (str/blank? body)
        {:error {:type "fetch_failed"
                 :message "Product page returned an empty response."}}

        :else
        {:ok {:status status
              :content-type (.contentType response)
              :body body}}))
    (catch Exception e
      (log/warn e "Failed to fetch product page.")
      {:error {:type "fetch_failed"
               :message "Unable to fetch product page."}})))

(defn parse-html
  [html]
  (let [doc (Jsoup/parse html)
        title (extract-title doc)
        raw-price (extract-price-text doc)
        price (parse-price raw-price)
        currency (extract-currency doc raw-price)]
    (if (nil? price)
      {:error {:type "parse_failed"
               :message "Unable to parse a price from this page."}}
      {:ok {:title title
            :price price
            :currency currency
            :raw-price-text raw-price
            :parser-version "auto-meta-v1"}})))

(defn fetch-product-details
  [config {:keys [url host]}]
  (if (denylisted-domain? config host)
    {:error {:type "domain_not_supported"
             :message "Domain is not supported for parsing."
             :details {:domain host}}}
    (let [{:keys [ok error]} (fetch-html config url)]
      (if error
        {:error error}
        (parse-html (:body ok))))))

(defn- localhost-host?
  [host]
  (when host
    (let [trimmed (str/replace host #"\.$" "")]
      (or (= "localhost" trimmed)
          (str/ends-with? trimmed ".localhost")))))

(defn- ipv6-unique-local?
  [^InetAddress addr]
  (when (instance? java.net.Inet6Address addr)
    (let [bytes (.getAddress addr)
          first-byte (bit-and (aget bytes 0) 0xff)]
      (= (bit-and first-byte 0xfe) 0xfc))))

(defn- private-address?
  [^InetAddress addr]
  (or (.isAnyLocalAddress addr)
      (.isLoopbackAddress addr)
      (.isLinkLocalAddress addr)
      (.isSiteLocalAddress addr)
      (.isMulticastAddress addr)
      (ipv6-unique-local? addr)))

(defn- ip-literal?
  [host]
  (when host
    (or (re-matches #"\d{1,3}(?:\.\d{1,3}){3}" host)
        (str/includes? host ":"))))

(defn- private-ip-literal?
  [host]
  (when (ip-literal? host)
    (try
      (private-address? (InetAddress/getByName host))
      (catch Exception _
        false))))

(defn- allowed-domain?
  [host allowed-domains]
  (let [host (normalize-host host)]
    (some (fn [domain]
            (let [domain (normalize-host domain)]
              (or (= host domain)
                  (str/ends-with? host (str "." domain)))))
          allowed-domains)))

(defn validate-url
  [config value]
  (let [{:keys [allowed-domains]} (:fetch config)
        parsed (parse-url value)]
    (cond
      (nil? parsed)
      {:error {:type "validation_error" :message "Invalid URL."}}

      (:user-info parsed)
      {:error {:type "validation_error" :message "URLs with credentials are not allowed."}}

      (localhost-host? (:host parsed))
      {:error {:type "validation_error" :message "Localhost URLs are not allowed."}}

      (private-ip-literal? (:host parsed))
      {:error {:type "validation_error" :message "Private or local addresses are not allowed."}}

      (and (seq allowed-domains) (not (allowed-domain? (:host parsed) allowed-domains)))
      {:error {:type "validation_error" :message "Domain is not allowed."}}

      :else
      (let [ascii-host (try
                         (IDN/toASCII (:host parsed))
                         (catch Exception _ (:host parsed)))]
        {:ok (assoc parsed :host ascii-host)}))))
