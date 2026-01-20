#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL is required."
  exit 1
fi

ready_url="${DATABASE_URL#jdbc:}"

echo "Waiting for database..."
for _ in {1..30}; do
  if pg_isready -d "$ready_url" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! pg_isready -d "$ready_url" >/dev/null 2>&1; then
  echo "Database not ready after waiting."
  exit 1
fi

echo "Running migrations (if needed)..."
clojure -M -m price-tracker.migrator

exec clojure -M -m price-tracker.main
