(ns price-tracker.fetch
  (:require [clojure.string :as str])
  (:import [java.net IDN InetAddress URI URLDecoder URLEncoder]))

(def ^:private max-url-length 2048)
(def ^:private tracking-params
  #{"utm_source" "utm_medium" "utm_campaign" "utm_term" "utm_content"
    "gclid" "fbclid" "mc_cid" "mc_eid" "ref" "ref_" "tag" "affid"
    "affiliate" "irclickid" "irgwc" "scid"})

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
