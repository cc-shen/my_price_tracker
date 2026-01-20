.PHONY: help frontend-dev frontend-build frontend-lint backend-dev backend-test migrate verify-migrations

help:
	@echo "Targets: frontend-dev, frontend-build, frontend-lint, backend-dev, backend-test, migrate, verify-migrations"

frontend-dev:
	cd frontend && npm run dev

frontend-build:
	cd frontend && npm run build

frontend-lint:
	cd frontend && npm run lint

backend-dev:
	cd backend && clojure -M:dev

backend-test:
	cd backend && clojure -M:test -m clojure.test

migrate:
	cd backend && clojure -M -m price-tracker.migrator

verify-migrations:
	./scripts/verify-migrations.sh
