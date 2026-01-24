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

`DENYLIST_CONFIG` points to a YAML file that blocks parsing for specific domains:
```yaml
denylist:
  - amazon.ca
```
The denylist is loaded at startup and cached; restart the service after changes.

## Migration verification
```sh
DATABASE_URL=jdbc:postgresql://postgres:postgres@localhost:5432/price_tracker \
  ./scripts/verify-migrations.sh
```

## API notes
`POST /api/products` requires a manual payload with `title` and `price`.
`POST /api/products/preview` can attempt to fetch and parse title/price once for a URL.
`POST /api/products/:id/fetch` attempts a one-off price fetch for a tracked product.
All timestamps are returned as UTC ISO-8601 strings.

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
