(ns price-tracker.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn datasource
  [database-url]
  (jdbc/get-datasource {:jdbcUrl database-url}))

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
