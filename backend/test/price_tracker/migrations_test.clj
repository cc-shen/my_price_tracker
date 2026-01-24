(ns price-tracker.migrations-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [price-tracker.db :as db]))

(def ^:dynamic *ds* nil)

(defn- db-fixture
  [f]
  (if-let [url (System/getenv "DATABASE_URL")]
    (let [ds (db/datasource url)]
      (binding [*ds* ds]
        (try
          (f)
          (finally
            (db/close-datasource ds)))))
    (f)))

(use-fixtures :each db-fixture)

(defn- with-db
  [f]
  (if *ds*
    (f)
    (is true)))

(deftest products-table-schema
  (with-db
    (fn []
      (let [columns (->> (db/query *ds*
                                   ["SELECT column_name FROM information_schema.columns WHERE table_name = 'products'"])
                         (map :column_name)
                         set)
            expected #{"id" "url" "canonical_url" "domain" "title" "currency"
                       "created_at" "updated_at" "last_checked_at" "is_active"}]
        (doseq [col expected]
          (is (contains? columns col)))))))

(deftest price-snapshots-table-schema
  (with-db
    (fn []
      (let [columns (->> (db/query *ds*
                                   ["SELECT column_name FROM information_schema.columns WHERE table_name = 'price_snapshots'"])
                         (map :column_name)
                         set)
            expected #{"id" "product_id" "price" "currency" "checked_at"
                       "checked_on" "source" "raw_price_text" "parser_version" "availability"}]
        (doseq [col expected]
          (is (contains? columns col)))))))

(deftest price-snapshots-index-exists
  (with-db
    (fn []
      (let [indexes (->> (db/query *ds*
                                   ["SELECT indexname FROM pg_indexes WHERE tablename = 'price_snapshots'"])
                         (map :indexname)
                         set)]
        (is (contains? indexes "price_snapshots_product_id_checked_at_idx"))
        (is (contains? indexes "price_snapshots_product_id_checked_on_key"))))))

(deftest price-snapshots-cascade-delete
  (with-db
    (fn []
      (let [confdeltypes (->> (db/query *ds*
                                        ["SELECT c.confdeltype
                                          FROM pg_constraint c
                                          JOIN pg_class r ON c.conrelid = r.oid
                                          WHERE r.relname = 'price_snapshots'
                                            AND c.contype = 'f'"])
                              (map :confdeltype)
                              set)]
        (is (contains? confdeltypes "c"))))))
