# Price Tracker

Local-only app intended to run on a Macbook; no public deployment target.

## Docker (backend + db)
```sh
docker compose up --build
```

Backend runs on `http://127.0.0.1:8080` and executes migrations on startup (only pending migrations are applied).

Frontend is currently behind a compose profile since the `frontend/` app is not scaffolded yet:
```sh
docker compose --profile frontend up --build
```

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
