ALTER TABLE price_snapshots
  ADD COLUMN IF NOT EXISTS checked_on date;

--;;

UPDATE price_snapshots
SET checked_on = (checked_at AT TIME ZONE 'UTC')::date
WHERE checked_on IS NULL;

--;;

WITH ranked AS (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY product_id, checked_on ORDER BY checked_at DESC) AS rn
  FROM price_snapshots
)
DELETE FROM price_snapshots ps
USING ranked r
WHERE ps.id = r.id
  AND r.rn > 1;

--;;

ALTER TABLE price_snapshots
  ALTER COLUMN checked_on SET NOT NULL;

--;;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'price_snapshots_product_id_checked_on_key'
  ) THEN
    ALTER TABLE price_snapshots
      ADD CONSTRAINT price_snapshots_product_id_checked_on_key
      UNIQUE (product_id, checked_on);
  END IF;
END $$;
