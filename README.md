# Price Tracker

Local-only app intended to run on a Macbook; no public deployment target.

## Environment
Create a local `.env` from the example and adjust credentials as needed:
```sh
cp .env.example .env
```
The `.env` file is ignored by git.

## Docker (frontend + backend + db)
```sh
docker compose up --build
```

Backend runs on `http://127.0.0.1:8080` and executes migrations on startup (only pending migrations are applied).
Frontend runs on `http://127.0.0.1:3000`.
Postgres data is persisted in the `pgdata` volume.

Note: the compose file defaults the backend `DATABASE_URL` to the `db` service hostname. If you run the backend
locally (outside Docker), update `DATABASE_URL` in `.env` to use `localhost`.

## Adding products
Use the Add Product modal. **Fetch details** attempts a one-time preview for supported domains only
and may be blocked for responsible crawling. Manual entry is always required to save a product.

## Timezones
All timestamps are stored and returned in UTC; the UI displays times in Eastern Time (EST/EDT).

## Make targets
```sh
make dev
make frontend-dev
make frontend-build
make frontend-lint
make backend-dev
make backend-test
make migrate
make verify-migrations
make clean
```

## Pre-commit
Run once when setting up the repo; this creates a local virtualenv, installs the git hook so checks run before each commit, and opens a shell with the venv active:
```sh
make dev
```

By default, `make dev` will use `python3.14` if available, falling back to `python3`.

Run all hooks manually:
```sh
pre-commit run --all-files
```

Note: the frontend ESLint hook requires dependencies installed (`cd frontend && yarn install`).
The Clojure hooks run via pre-commit and are scoped to `backend/` sources.
