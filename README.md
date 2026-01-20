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
make frontend-dev
make frontend-build
make frontend-lint
make backend-dev
make backend-test
make migrate
make verify-migrations
```
