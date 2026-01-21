(ns price-tracker.store
  (:require [clojure.string :as str]
            [price-tracker.db :as db]))

(defn create-product!
  [ds {:keys [url canonical-url domain title currency]}]
  (db/execute-one
   ds
   ["INSERT INTO products (url, canonical_url, domain, title, currency)
     VALUES (?, ?, ?, ?, ?)
     RETURNING *"
    url canonical-url domain title currency]))

(defn get-product
  [ds id]
  (db/execute-one
   ds
   ["SELECT * FROM products WHERE id = ?" id]))

(defn delete-product!
  [ds id]
  (db/execute-one
   ds
   ["DELETE FROM products WHERE id = ? RETURNING id" id]))

(defn list-products
  [ds]
  (db/query
   ds
   ["SELECT p.id,
            p.title,
            p.domain,
            p.currency,
            p.last_checked_at,
            ps.price AS current_price,
            ps.currency AS current_currency
     FROM products p
     LEFT JOIN LATERAL (
       SELECT price, currency, checked_at
       FROM price_snapshots
       WHERE product_id = p.id
       ORDER BY checked_at DESC
       LIMIT 1
     ) ps ON true
     ORDER BY p.updated_at DESC"]))

(defn insert-price-snapshot!
  [ds {:keys [product-id price currency source raw-price-text parser-version availability checked-at]}]
  (db/execute-one
   ds
   ["INSERT INTO price_snapshots (product_id, price, currency, source, raw_price_text, parser_version, availability, checked_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     RETURNING *"
    product-id price currency source raw-price-text parser-version availability checked-at]))

(defn update-last-checked!
  [ds {:keys [product-id checked-at]}]
  (db/execute-one
   ds
   ["UPDATE products
     SET last_checked_at = ?, updated_at = now()
     WHERE id = ?
     RETURNING *"
    checked-at product-id]))

(defn price-history
  [ds {:keys [product-id from to]}]
  (let [base-sql (str/join
                  " "
                  ["SELECT checked_at, price, currency"
                   "FROM price_snapshots"
                   "WHERE product_id = ?"])
        [sql params] (cond-> [base-sql [product-id]]
                       from (->
                             (update 0 str " AND checked_at >= ?")
                             (update 1 conj from))
                       to (->
                           (update 0 str " AND checked_at <= ?")
                           (update 1 conj to))
                       true (->
                             (update 0 str " ORDER BY checked_at ASC")))]
    (db/query ds (into [sql] params))))
