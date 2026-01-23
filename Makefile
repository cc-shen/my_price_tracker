.PHONY: help frontend-dev frontend-build frontend-lint frontend-test backend-dev backend-setup backend-test test-backend test migrate verify-migrations pre-commit-install dev clean venv docker-up docker-down docker-restart docker-logs docker-ps

PYTHON_BIN ?= python3.14
VENV_DIR ?= .venv
VENV_PYTHON := $(VENV_DIR)/bin/python
PYTHON := $(shell if command -v $(PYTHON_BIN) >/dev/null 2>&1; then echo $(PYTHON_BIN); else echo python3; fi)

help:
	@echo "Targets: frontend-dev, frontend-build, frontend-lint, frontend-test, backend-dev, backend-setup, backend-test, test-backend, test, migrate, verify-migrations, pre-commit-install, dev, clean, docker-up, docker-down, docker-restart, docker-logs, docker-ps"

frontend-dev:
	cd frontend && yarn run dev

frontend-build:
	cd frontend && yarn run build

frontend-lint:
	cd frontend && yarn run lint

frontend-test:
	cd frontend && if node -e "const pkg=require('./package.json'); process.exit(pkg.scripts&&pkg.scripts.test?0:1)"; then yarn run test; else echo "frontend: no test script defined, skipping"; fi

backend-dev:
	cd backend && clojure -M:dev

backend-setup:
	@if [ -n "$$DATABASE_URL" ]; then echo "backend: running migrations"; $(MAKE) migrate; else echo "backend: DATABASE_URL not set; skipping migrations"; fi

backend-test:
	cd backend && clojure -M:test -m price-tracker.test-runner

test-backend:
	./scripts/test-backend.sh

test: test-backend frontend-test

migrate:
	cd backend && clojure -M -m price-tracker.migrator

verify-migrations:
	./scripts/verify-migrations.sh

venv:
	@$(PYTHON) -m venv $(VENV_DIR)

pre-commit-install: venv
	@$(VENV_PYTHON) -m pip install --upgrade pip
	@$(VENV_PYTHON) -m pip install pre-commit
	@$(VENV_PYTHON) -m pre_commit install

dev: pre-commit-install
	@./scripts/dev-shell.sh

clean:
	rm -rf $(VENV_DIR)

docker-up:
	docker compose up -d

docker-down:
	docker compose down

docker-restart:
	docker compose restart

docker-logs:
	docker compose logs -f --tail=200

docker-ps:
	docker compose ps
