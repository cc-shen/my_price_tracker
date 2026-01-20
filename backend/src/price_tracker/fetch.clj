(ns price-tracker.fetch
  (:require [clojure.string :as str])
  (:import [java.net IDN InetAddress URI]
           [java.net.http HttpClient HttpClient$Redirect HttpRequest HttpResponse$BodyHandlers]
           [java.time Duration]))

(def ^:private max-url-length 2048)
(def ^:private default-user-agent "price-tracker/0.1")

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

(defn resolve-host
  [host]
  (InetAddress/getAllByName host))

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

      (and (seq allowed-domains) (not (allowed-domain? (:host parsed) allowed-domains)))
      {:error {:type "validation_error" :message "Domain is not allowed."}}

      :else
      (let [ascii-host (try
                         (IDN/toASCII (:host parsed))
                         (catch Exception _ (:host parsed)))]
        (try
          (let [addresses (resolve-host ascii-host)]
            (if (some private-address? addresses)
              {:error {:type "validation_error" :message "Private or local addresses are not allowed."}}
              {:ok (assoc parsed :host ascii-host)}))
          (catch Exception _
            {:error {:type "validation_error" :message "Unable to resolve host."}}))))))

(defn fetch-html
  [config url]
  (let [{:keys [timeout-ms user-agent]} (:fetch config)
        timeout-ms (or timeout-ms 10000)
        user-agent (or user-agent default-user-agent)
        {:keys [ok error]} (validate-url config url)]
    (if error
      {:error error}
      (let [client (-> (HttpClient/newBuilder)
                       (.connectTimeout (Duration/ofMillis timeout-ms))
                       (.followRedirects HttpClient$Redirect/NEVER)
                       (.build))
            request (-> (HttpRequest/newBuilder (:uri ok))
                        (.timeout (Duration/ofMillis timeout-ms))
                        (.header "User-Agent" user-agent)
                        (.GET)
                        (.build))
            response (.send client request (HttpResponse$BodyHandlers/ofString))
            status (.statusCode response)]
        (if (<= 200 status 299)
          {:ok {:status status
                :body (.body response)}}
          {:error {:type "fetch_error"
                   :message "Fetch failed."
                   :status status}})))))
