(ns price-tracker.test-runner
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t]))

(defn- file->ns
  [^java.io.File f]
  (let [root (.getCanonicalPath (io/file "test"))
        path (.getCanonicalPath f)
        rel (when (str/starts-with? path root)
              (subs path (inc (count root))))]
    (some-> rel
            (str/replace #"\.clj$" "")
            (str/replace "/" ".")
            (str/replace "_" "-")
            symbol)))

(defn -main
  [& _]
  (let [files (->> (file-seq (io/file "test"))
                   (filter #(.isFile %))
                   (filter #(str/ends-with? (.getName %) ".clj")))
        namespaces (->> files (map file->ns) (remove nil?) distinct)]
    (doseq [ns namespaces]
      (require ns))
    (let [summary (apply t/run-tests namespaces)]
      (when-not (t/successful? summary)
        (System/exit 1)))))
