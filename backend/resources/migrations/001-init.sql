CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  url text NOT NULL UNIQUE,
  canonical_url text UNIQUE,
  domain text NOT NULL,
  title text NOT NULL,
  currency text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_checked_at timestamptz,
  is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS price_snapshots (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  price numeric(12,2) NOT NULL,
  currency text,
  checked_at timestamptz NOT NULL DEFAULT now(),
  source text NOT NULL,
  raw_price_text text
);

CREATE INDEX IF NOT EXISTS price_snapshots_product_id_checked_at_idx
  ON price_snapshots (product_id, checked_at DESC);
