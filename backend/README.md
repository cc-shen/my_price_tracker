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

`DATABASE_URL` is required for database access and migrations. `postgresql://` and
`jdbc:postgresql://` formats are both accepted.

## Migration verification
```sh
DATABASE_URL=jdbc:postgresql://postgres:postgres@localhost:5432/price_tracker \
  ./scripts/verify-migrations.sh
```

## API notes
`POST /api/products` expects `url` only; the backend fetches and parses title/price.

## Tests
```sh
clojure -M:test -m price-tracker.test-runner
```

To run tests against a temporary Postgres container:
```sh
make test-backend
```

## Make targets
From the repo root:
```sh
make backend-dev
make backend-test
make migrate
```
