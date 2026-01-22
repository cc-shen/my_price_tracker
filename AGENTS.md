# Repository Guidelines

## Overview
This repository uses a React + TypeScript + Tailwind CSS frontend bundled with rspack, and a Clojure backend. Database usage is intentionally undecided; keep persistence optional and document any choice before introducing a dependency.

## Always-Read Docs
- Before starting any task in this repo, review `REQUIREMENTS.md` and `PLAN.md` for scope, constraints, and current milestones.
- Keep changes aligned with the requirements (functional + non-functional) and the milestone plan.

## Requirements & Plan Highlights
- Core MVP: add product by URL, dashboard list, product detail with price history chart + range presets, delete with confirmation, manual refresh.
- Security is mandatory: SSRF protections, input validation, rate limiting, safe logging, and SQL injection protection.
- Deployment is local-only (run on a Macbook), with no public exposure of services.
- Persistence: recommended Postgres with migrations and cascade deletes; document final choice before adding a dependency.
- Deployment: Docker/Podman with separate frontend/backend/DB containers and persistent volumes.
- Parsing: domain-based parser registry with DOM → JSON-LD → OpenGraph → regex fallback.

## Project Structure & Module Organization
- `frontend/`: React app (e.g., `frontend/src/`, `frontend/public/`).
- `frontend/src/`: UI modules by feature (e.g., `frontend/src/pricing/`).
- `frontend/tests/`: frontend tests mirroring `frontend/src/`.
- `backend/`: Clojure service (e.g., `backend/src/`, `backend/resources/`).
- `backend/test/`: backend tests mirroring `backend/src/`.
- `docs/`: design notes and architecture diagrams.
- `scripts/`: developer utilities (setup, data refresh, etc.).
- `.env.example`: documented environment variables; keep secrets out of Git.

## Build, Test, and Development Commands
Define these once the tooling is wired up; examples below show the intended shape:
- `cd frontend && yarn run dev` — run rspack dev server with hot reload.
- `cd frontend && yarn run build` — produce a production bundle.
- `cd frontend && yarn run lint` — run TypeScript/ESLint checks.
- `cd backend && clojure -M:dev` — start the Clojure service locally.
- `cd backend && clojure -M:test` — run backend tests.

## Coding Style & Naming Conventions
- Frontend: 2-space indentation, Prettier + ESLint, Tailwind class order via Prettier plugin.
- TypeScript: `camelCase` vars/functions, `PascalCase` React components and types.
- Clojure: follow community style (`kebab-case` vars, `*earmuff*` for dynamic vars).
- File names: `kebab-case` for components and assets; Clojure namespaces mirror paths.

## Testing Guidelines
Pick frameworks when tests land (e.g., Vitest/Jest for frontend, clojure.test for backend).
- Frontend test files: `*.test.ts` or `*.test.tsx` under `frontend/tests/`.
- Backend test namespaces mirror source namespaces under `backend/test/`.
- New features should include tests or a brief note explaining why not.

## Commit & Pull Request Guidelines
There is no commit history yet. Use a clear, conventional style going forward, e.g.:
- `feat: add price ingestion pipeline`
- `fix: handle missing currency codes`
Pull requests should include a short description, linked issues (if any), and test notes.

## Data Storage (Pending Decision)
If you add a database, document:
- Choice and version (e.g., Postgres 16, SQLite).
- Migration strategy and tooling.
- Local setup steps and required environment variables.

## Security & Configuration Tips
- Never commit secrets; keep credentials in `.env` and provide `.env.example`.
- Document required API keys in `docs/` or the README when added.
