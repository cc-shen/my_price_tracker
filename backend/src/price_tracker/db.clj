(ns price-tracker.db
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn normalize-jdbc-url
  [database-url]
  (when database-url
    (cond
      (str/starts-with? database-url "jdbc:")
      database-url

      (str/starts-with? database-url "postgresql://")
      (str/replace database-url "postgresql://" "jdbc:postgresql://")

      (str/starts-with? database-url "postgres://")
      (str/replace database-url "postgres://" "jdbc:postgresql://")

      :else
      database-url)))

(defn datasource
  [database-url]
  (jdbc/get-datasource {:jdbcUrl (normalize-jdbc-url database-url)}))

(defn close-datasource
  [ds]
  (when (instance? java.io.Closeable ds)
    (.close ^java.io.Closeable ds)))

(defn execute-one
  ([ds sql-params]
   (execute-one ds sql-params {}))
  ([ds sql-params opts]
   (jdbc/execute-one! ds sql-params (merge {:builder-fn rs/as-unqualified-lower-maps} opts))))

(defn execute
  ([ds sql-params]
   (execute ds sql-params {}))
  ([ds sql-params opts]
   (jdbc/execute! ds sql-params (merge {:builder-fn rs/as-unqualified-lower-maps} opts))))

(defn query
  ([ds sql-params]
   (query ds sql-params {}))
  ([ds sql-params opts]
   (jdbc/execute! ds sql-params (merge {:builder-fn rs/as-unqualified-lower-maps} opts))))
