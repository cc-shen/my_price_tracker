ALTER TABLE price_snapshots
  DROP COLUMN IF EXISTS parser_version,
  DROP COLUMN IF EXISTS availability;
