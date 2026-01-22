#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "backend tests require docker to start a temporary Postgres container"
  exit 1
fi

DB_NAME="${TEST_DB_NAME:-}"
DB_USER="${TEST_DB_USER:-${POSTGRES_USER:-}}"
DB_PASSWORD="${TEST_DB_PASSWORD:-${POSTGRES_PASSWORD:-}}"
HOST_PORT="55432"

if [[ -z "${DB_NAME}" || -z "${DB_USER}" || -z "${DB_PASSWORD}" ]]; then
  echo "backend tests require TEST_DB_NAME/TEST_DB_USER/TEST_DB_PASSWORD (or POSTGRES_USER/POSTGRES_PASSWORD) to be set"
  exit 1
fi

container_id=$(docker run --rm -d \
  -e POSTGRES_DB="${DB_NAME}" \
  -e POSTGRES_USER="${DB_USER}" \
  -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
  -p "127.0.0.1:${HOST_PORT}:5432" \
  postgres:16)

cleanup() {
  docker rm -f "${container_id}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "backend: waiting for postgres to be ready"
for _ in {1..30}; do
  if docker exec "${container_id}" pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker exec "${container_id}" pg_isready -U "${DB_USER}" -d "${DB_NAME}" >/dev/null 2>&1; then
  echo "backend: postgres did not become ready in time"
  exit 1
fi

export DATABASE_URL="postgresql://${DB_USER}:${DB_PASSWORD}@127.0.0.1:${HOST_PORT}/${DB_NAME}"

echo "backend: running migrations"
cd backend
clojure -M -m price-tracker.migrator

echo "backend: running tests"
clojure -M:test -m price-tracker.test-runner
