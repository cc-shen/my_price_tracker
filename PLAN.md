# Plan for Price Tracker Website

## Milestone 0: Repo baseline & decisions

- [x] Verify repo structure (`frontend/`, `backend/`, `docs/`, `scripts/`).
- [x] Document local-only deployment requirement (localhost binding, no public exposure).
- [x] Decide and document persistence choice (default: PostgreSQL as suggested in requirements).
- [x] Add/extend `docs/architecture.md` with DB choice, migration tool, and rationale.
- [x] Add `.env.example` with backend/frontend variables from requirements.
- [x] Standardize frontend package management on Yarn and commit `yarn.lock`.

## Milestone 1: Backend foundation (API + DB)

### 1.1 Service skeleton

- [x] Create Ring + Reitit app with Integrant/Mount lifecycle.
- [x] Add config loading from env vars (`DATABASE_URL`, `PORT`, etc.).
- [x] Add basic health endpoint and CORS (if needed for local dev only).

### 1.2 Database schema + migrations

- [x] Add migration tooling (Migratus or Flyway) and baseline migration.
- [x] Create `products` and `price_snapshots` tables with indexes and cascade delete.
- [x] Define DB access layer with `next.jdbc` and query helpers.

### 1.3 REST endpoints (CRUD)

- [x] `POST /api/products` (add by URL)
- [x] `GET /api/products` (list with current price)
- [x] `GET /api/products/:id` (product detail)
- [x] `GET /api/products/:id/prices?from=&to=` (history)
- [x] `POST /api/products/:id/refresh` (manual refresh)
- [x] `DELETE /api/products/:id` (delete product)
- [x] Validation and error responses (400/404/422/500) with consistent payloads.

## Milestone 2: Manual entry + validation

### 2.1 URL validation

- [x] Implement URL validation: http/https only, length limits.
- [x] Block private IP ranges and localhost for IP literals.
- [x] Add basic rate limiting per endpoint.

### 2.2 Manual entry validation

- [x] Require title + price for manual product creation.
- [x] Validate currency format (optional) and numeric price input.

## Milestone 3: Core backend flows

- [x] Add product:
  - [x] Normalize URL
  - [x] Validate manual metadata
  - [x] Insert product + initial snapshot
- [x] Manual refresh:
  - [x] Accept manual price input
  - [x] Append snapshot
- [x] Delete product:
  - [x] Confirmed delete
  - [x] Cascade snapshots
- [x] List products:
  - [x] Return current price and last updated timestamp
- [x] Price history:
  - [x] Query by range; ordered by time

## Milestone 4: Frontend foundation

### 4.1 App shell + routing

- [x] Initialize React + TypeScript + Tailwind + rspack.
- [x] Create routes: `/` (dashboard), `/products/:id` (detail).
- [x] Add API client layer (fetch wrapper or React Query).

### 4.2 UI components

- [x] Product list cards/rows with title, price, domain, last updated, delta.
- [x] Add product modal with URL input and validation errors.
- [x] Delete confirmation dialog.
- [x] Toast notifications for success/error states.

## Milestone 5: Price chart + filters

- [x] Build chart component (e.g., Recharts or lightweight SVG).
- [x] Add range presets: 7/30/90/365/all.
- [x] Handle 1-point series and gaps gracefully.

## Milestone 6: Testing

### 6.1 Backend tests

- [x] Integration tests for CRUD and price history endpoints.
- [x] Migration tests to confirm schema expectations.

### 6.2 Frontend tests

- [x] Component tests for add product flow.
- [x] Delete confirmation behavior.
- [x] Chart rendering with mocked data.

## Milestone 7: Containerization & docs

- [x] Add `docker-compose.yml` with frontend, backend, Postgres, and volumes.
- [x] Document local dev commands and env setup in README or `docs/`.
- [x] Bind services to localhost only and confirm persistence survives container restarts.

## Milestone 8: Post-MVP enhancements

- Search/sort filters and improved canonical URL handling.
- Failure tracking and retries; better domain error reporting.
