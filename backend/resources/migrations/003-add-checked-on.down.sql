ALTER TABLE price_snapshots
  DROP CONSTRAINT IF EXISTS price_snapshots_product_id_checked_on_key;

--;;

ALTER TABLE price_snapshots
  DROP COLUMN IF EXISTS checked_on;
