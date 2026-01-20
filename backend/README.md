# Backend

## Run locally
```sh
clojure -M -m price-tracker.main
```

## Migrations
```sh
clojure -M -m price-tracker.migrator
```

## Environment
See the root `.env.example` for required variables.

`DATABASE_URL` is required for database access and migrations.

## Migration verification
```sh
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/price_tracker \
  ./scripts/verify-migrations.sh
```

## API notes
`POST /api/products` currently expects `url`, `title`, and numeric `price` (plus optional `currency`)
until parser support lands in Milestone 2.

## Tests
```sh
clojure -M:test -m clojure.test
```

## Make targets
From the repo root:
```sh
make backend-dev
make backend-test
make migrate
```
