# Plan for Price Tracker Website

## Milestone 0: Repo baseline & decisions

- Verify repo structure (`frontend/`, `backend/`, `docs/`, `scripts/`).
- Document local-only deployment requirement (localhost binding, no public exposure).
- Decide and document persistence choice (default: PostgreSQL as suggested in requirements).
- Add/extend `docs/architecture.md` with DB choice, migration tool, and rationale.
- Add `.env.example` with backend/frontend variables from requirements.
- Standardize frontend package management on Yarn and commit `yarn.lock`.

## Milestone 1: Backend foundation (API + DB)

### 1.1 Service skeleton

- Create Ring + Reitit app with Integrant/Mount lifecycle.
- Add config loading from env vars (`DATABASE_URL`, `PORT`, etc.).
- Add basic health endpoint and CORS (if needed for local dev only).

### 1.2 Database schema + migrations

- Add migration tooling (Migratus or Flyway) and baseline migration.
- Create `products` and `price_snapshots` tables with indexes and cascade delete.
- Define DB access layer with `next.jdbc` and query helpers.

### 1.3 REST endpoints (CRUD)

- `POST /api/products` (add by URL)
- `GET /api/products` (list with current price)
- `GET /api/products/:id` (product detail)
- `GET /api/products/:id/prices?from=&to=` (history)
- `POST /api/products/:id/refresh` (manual refresh)
- `DELETE /api/products/:id` (delete product)
- Validation and error responses (400/404/422/500) with consistent payloads.

## Milestone 2: Fetching + parsing system

### 2.1 Fetching & SSRF protections

- Implement URL validation: http/https only, length limits.
- Block private IP ranges and localhost; validate DNS resolution defensively.
- Add request timeouts, user agent, and basic rate limiting per endpoint.

### 2.2 Parser registry

- Build a registry keyed by domain (exact + suffix match).
- Parsing pipeline order: DOM selectors -> JSON-LD -> OpenGraph -> regex fallback.
- Store `raw_price_text`, `parser_version`, and optional availability metadata.

### 2.3 Initial retailer support

- Implement 1-2 domain parsers (e.g., Amazon + Lululemon) as MVP.
- Add HTML fixture samples for unit tests.

## Milestone 3: Core backend flows

- Add product:
  - Normalize URL
  - Fetch metadata
  - Insert product + initial snapshot
- Manual refresh:
  - Fetch latest price
  - Append snapshot
- Delete product:
  - Confirmed delete
  - Cascade snapshots
- List products:
  - Return current price and last updated timestamp
- Price history:
  - Query by range; ordered by time

## Milestone 4: Frontend foundation

### 4.1 App shell + routing

- Initialize React + TypeScript + Tailwind + rspack.
- Create routes: `/` (dashboard), `/products/:id` (detail).
- Add API client layer (fetch wrapper or React Query).

### 4.2 UI components

- Product list cards/rows with title, price, domain, last updated, delta.
- Add product modal with URL input and validation errors.
- Delete confirmation dialog.
- Toast notifications for success/error states.

## Milestone 5: Price chart + filters

- Build chart component (e.g., Recharts or lightweight SVG).
- Add range presets: 7/30/90/365/all.
- Handle 1-point series and gaps gracefully.

## Milestone 6: Testing

### 6.1 Backend tests

- Parser unit tests with saved HTML fixtures.
- Integration tests for CRUD and price history endpoints.
- Migration tests to confirm schema expectations.

### 6.2 Frontend tests

- Component tests for add product flow.
- Delete confirmation behavior.
- Chart rendering with mocked data.

## Milestone 7: Containerization & docs

- Add `docker-compose.yml` with frontend, backend, Postgres, and volumes.
- Document local dev commands and env setup in README or `docs/`.
- Bind services to localhost only and confirm persistence survives container restarts.

## Milestone 8: Post-MVP enhancements

- Scheduler for periodic refresh + rate limiting.
- Search/sort filters and improved canonical URL handling.
- Failure tracking and retries; better domain error reporting.
