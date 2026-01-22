ALTER TABLE price_snapshots
  ADD COLUMN IF NOT EXISTS parser_version text,
  ADD COLUMN IF NOT EXISTS availability text;
