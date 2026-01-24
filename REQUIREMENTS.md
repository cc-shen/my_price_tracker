# Price Tracker Website — Requirements Document (v0.1)

## 1) Overview

### 1.1 Purpose
Build a web application that allows a user to **track product prices across multiple retail websites** by submitting product links. The app collects and stores each product’s price over time and displays **price history graphs** with filtering options.

### 1.1.1 Local-only deployment requirement
This application is intended to run **locally on a Macbook** and is **not meant to be publicly exposed**. Services should bind to localhost by default and avoid public internet ingress.
Do not open firewall rules, port-forwarding, or public tunnels for this app.

### 1.2 Goals
- Track products from multiple websites (Amazon, Lululemon, Aritzia, Jimmy Choo, etc.).
- Manually enter product title and price for every product.
- Allow an optional, user-initiated **preview fetch** to prefill title/price on supported domains only (responsible crawling; not guaranteed).
- Display:
  - “All tracked items” overview page
  - Individual product detail page with **price history chart**
- Allow management:
  - Add product by URL
  - Delete product (with confirmation)
- Deployable via **Docker/Podman** with reliable persistence
- Local-only operation; no public hosting requirement

### 1.3 Non-Goals (for initial version)
- Price drop alerts (email/push)
- Multi-user accounts / authentication (unless explicitly needed)
- Browser extension integration
- Full internationalization (multi-language)
- Complex anti-bot bypass / CAPTCHA solving
- Automated scheduled scraping or background crawling
- Public hosting or internet-exposed services


## 2) Users & Use Cases

### 2.1 Primary User
- Single user (you), tracking personal shopping items across different websites.

### 2.2 Core Use Cases
1. **Add Product**
   - User pastes URL + enters title + current price → saves product and initial price point.
2. **View All Products**
   - User sees a dashboard list/grid of all tracked products with current price and change summary.
3. **View Price History**
   - User opens a product → sees a chart of price points with selectable date range filters.
4. **Delete Product**
   - User deletes a product → app prompts confirmation → on confirm, product is removed.


## 3) Functional Requirements

### 3.1 Product Tracking

#### FR-1: Add product by URL
**User story:** As a user, I want to add a product by URL so I can track its price over time.

**Requirements:**
- Input: `product_url`
- On submission:
  - Validate URL format
  - Normalize URL (strip tracking parameters if possible)
  - Require manual metadata entry (always required, even if a preview fetch is available):
    - `title`
    - `price` (numeric)
    - `currency` (e.g., CAD/USD; optional)
  - Store product record
  - Store initial price point in price history

**Acceptance Criteria:**
- If manual entry succeeds, product appears in the dashboard immediately.
- If validation fails, show a helpful error (e.g., “title and price are required”).

#### FR-1b: Preview product details (limited automatic fetch)
**User story:** As a user, I want to preview a product’s title and price so I can save time when adding it.

**Requirements:**
- Preview fetch is **user-initiated** and runs once per action (no background scraping).
- Only works for a subset of domains; unsupported or blocked domains must fail gracefully.
- Returned data is **advisory**; user must review and confirm manual fields before saving.
- Respect responsible crawling practices (rate limits, denylist, no anti-bot bypass).

**Acceptance Criteria:**
- If parsing succeeds, the modal is prefilled with title/price for review.
- If parsing fails or the domain is blocked, the UI clearly instructs manual entry.

#### FR-2: Support multiple retailer domains
**Requirements:**
- System should support tracking URLs from multiple retailer domains (Amazon, Lululemon, Aritzia, etc.).
- Domain should be captured for display and filtering.

**Acceptance Criteria:**
- URLs from different supported domains can be added and listed.


### 3.2 Price History

#### FR-3: Record price snapshots over time
**Requirements:**
- Each tracked product should have a history of price records:
  - timestamp
  - price
  - currency
  - optional metadata (entry source, entry version)
  - one snapshot per product per **UTC day** (same-day updates overwrite)

**Acceptance Criteria:**
- At minimum, adding a product creates 1 data point.
- System supports repeated snapshot insertions across days.

#### FR-4: Display price history graph
**User story:** As a user, I want to see a graph of a product’s price changes over time.

**Requirements:**
- Product detail page must show:
  - Line chart of price vs time
  - Latest price and last updated timestamp
  - (Optional) min/avg/max for selected range

**Acceptance Criteria:**
- Graph renders correctly for 1 point and N points.
- Handles missing days (gaps) gracefully.

#### FR-5: Date range filters on chart
**Requirements:**
- Provide preset filters:
  - 7 days
  - 30 days
  - 90 days
  - 1 year
  - All time
- (Optional) custom date picker range

**Acceptance Criteria:**
- Selecting a filter updates the graph data shown.
- Backend query should support range filtering efficiently.


### 3.3 Dashboard / Overview Page

#### FR-6: View all tracked products at a glance
**Requirements:**
- Show a table or card grid with per-product:
  - Title
  - Current price
  - Currency
  - Retailer domain
  - Last updated time
  - Price change indicator (e.g., Δ since last snapshot or since added)
- Sorting options:
  - Last updated
  - Highest price
  - Biggest drop (optional)
- Search/filter by title or domain (optional)

**Acceptance Criteria:**
- Dashboard loads quickly and is readable even with ~100 products.


### 3.4 Delete Product

#### FR-7: Delete a product from tracking
**Requirements:**
- Provide a delete action on dashboard and/or product page.
- Before deletion, show a confirmation dialog:
  - “Are you sure? This will permanently delete the product and its price history.”
- Delete should remove product + all associated price history records.

**Acceptance Criteria:**
- Cancel does nothing.
- Confirm deletes and removes from UI immediately after success.


### 3.5 Data Fetching Behavior (Manual + Limited Preview)

#### FR-8: Manual refresh (MVP)
**Requirements:**
- Provide a “Refresh price” button per product or on dashboard.
- On refresh:
  - user enters the latest price (and optional currency)
  - overwrite the existing snapshot for the same **UTC day**, or create a new one if none exists

**Acceptance Criteria:**
- Refresh overwrites today’s record in UTC (if present) and updates the timestamp.

#### FR-9: One-off fetch for tracked products (limited)
**Requirements:**
- Provide a “Fetch” action that attempts a one-time parse of the tracked product URL.
- Only works for supported domains; failures should instruct manual update.
- Uses the same same-day overwrite rule as manual refresh (UTC day).

**Acceptance Criteria:**
- If parsing succeeds, the latest price is stored and the UI updates.
- If parsing fails, the user is prompted to enter a manual update.


## 4) Non-Functional Requirements

### 4.1 Performance
- Dashboard should load in < 1s for ~100 products (local deployment).
- Price history graph query should return in < 300ms for typical ranges.

### 4.2 Reliability & Data Integrity
- Price records are append-only across days; the system stores at most one snapshot per product per **UTC day** (same-day overwrites).
- Deleting product removes all dependent data (cascading delete).
- DB schema supports migration versioning.
- All timestamps are stored and returned in UTC (ISO-8601).

### 4.3 Security (Important)
**Key requirements:**
- Input validation:
  - URL length limits
  - Store normalized canonical URLs
- Secure headers:
  - Content Security Policy (CSP)
  - X-Frame-Options / frame-ancestors
  - Strict-Transport-Security (only if ever deployed publicly)
- Backend secrets:
  - Stored only in environment variables (no hardcoding)
- Rate limiting:
  - Prevent abuse of product creation and refresh endpoints
- Logging:
  - Never log sensitive headers/cookies

### 4.4 Time Display
- UI must display timestamps in Eastern Time (America/New_York) with DST handling (EST/EDT).

### 4.5 Maintainability / Extensibility
- Clear separation between:
  - persistence layer
  - API layer
  - UI layer


## 5) Suggested Technical Architecture

### 5.1 Frontend (Rspack + React + TypeScript + Tailwind CSS)
**Package management**
- Use Yarn and commit `yarn.lock` to freeze dependency versions.

**Pages**
- `/` Dashboard
- `/products/:id` Product details with chart

**UI Components**
- ProductCard / ProductRow
- AddProductModal
- ConfirmDeleteDialog
- PriceChart component with range selector
- Toast/notification component

**State management**
- Use a fetch/cache layer such as React Query (or equivalent)
- Minimal local state for UI interactions


### 5.2 Backend (Clojure)
**Responsibilities**
- REST API for CRUD and querying price history
- Manual product entry and price updates
- Optional user-initiated preview/fetch for supported domains (no background scraping)

**Suggested libraries**
- Ring + Reitit for routing
- next.jdbc for DB access
- Integrant/Mount for lifecycle management


### 5.3 Database (PostgreSQL)
Recommended for persistence and efficient time-series queries.

#### Data Model

**products**
- `id` (uuid, pk)
- `url` (text, unique)
- `canonical_url` (text, unique, optional)
- `domain` (text)
- `title` (text)
- `currency` (text) — optional
- `created_at` (timestamptz)
- `updated_at` (timestamptz)
- `last_checked_at` (timestamptz, nullable)
- `is_active` (boolean, default true)

**price_snapshots**
- `id` (uuid, pk)
- `product_id` (uuid, fk → products.id ON DELETE CASCADE)
- `price` (numeric(12,2))
- `currency` (text)
- `checked_at` (timestamptz)
- `checked_on` (date, UTC)
- `source` (text, e.g., “manual-entry”, “manual-refresh”)
- `raw_price_text` (text, optional for debugging)
- Unique: `(product_id, checked_on)`
- Index: `(product_id, checked_at desc)`


## 6) API Requirements (REST)

All timestamp fields in API responses must be ISO-8601 UTC strings.

### 6.1 Preview product details (limited)
`POST /api/products/preview`
```json
{
  "url": "https://..."
}
```

Response (best effort):
```json
{
  "url": "https://...",
  "domain": "example.com",
  "title": "Product name",
  "price": 123.45,
  "currency": "CAD",
  "parserVersion": "auto-meta-v1",
  "rawPriceText": "$123.45"
}
```

### 6.2 Add product
`POST /api/products`
```json
{
  "url": "https://...",
  "manual": {
    "title": "Product name",
    "price": 123.45,
    "currency": "CAD"
  }
}
```

Response:
```json
{
  "id": "uuid",
  "title": "Product name",
  "price": 123.45,
  "currency": "CAD",
  "domain": "amazon.ca",
  "lastCheckedAt": "..."
}
```

### 6.3 List products
`GET /api/products`

Response:
```json
[
  {
    "id": "uuid",
    "title": "...",
    "domain": "...",
    "currentPrice": 123.45,
    "currency": "CAD",
    "lastCheckedAt": "..."
  }
]
```

### 6.4 Get single product
`GET /api/products/:id`

### 6.5 Get price history (with range filters)
`GET /api/products/:id/prices?from=2025-01-01&to=2026-01-20`

Response:
```json
{
  "productId": "uuid",
  "points": [
    { "t": "2026-01-01T12:00:00Z", "price": 100.00 },
    { "t": "2026-01-05T12:00:00Z", "price": 95.00 }
  ]
}
```

### 6.6 Refresh price manually
`POST /api/products/:id/refresh`
```json
{
  "price": 120.00,
  "currency": "CAD"
}
```

### 6.7 Fetch latest price (limited)
`POST /api/products/:id/fetch`

### 6.8 Delete product
`DELETE /api/products/:id`


## 8) Deployment Requirements (Docker/Podman)

### 8.1 Containerization
- One container for backend
- One container for frontend
- One container for PostgreSQL
- Compose file for local dev: `docker-compose.yml` (or Podman equivalent)
- Bind services to localhost only; do not expose public ports by default

### 8.2 Environment Variables
Backend:
- `DATABASE_URL`
- `PORT`
- `LOG_LEVEL`
- `ALLOWED_DOMAINS` (optional allowlist)
- `DENYLIST_CONFIG` (optional denylist path)
- `RATE_LIMIT_PER_MINUTE`

Frontend:
- `API_BASE_URL`

### 8.3 Persistence
- Postgres data must be stored in a volume so it survives restarts.


## 9) Observability & Logging

### 9.1 Logging
Log important events:
- product added
- refresh success/failure
- validation failures with minimal context (avoid logging secrets)

### 9.2 Metrics (optional)
- number of tracked products
- per-domain failure rate


## 10) Testing Requirements

### 10.1 Backend tests
- Integration tests for API endpoints
- DB migration tests

### 10.2 Frontend tests
- Component tests for:
  - add product flow
  - delete confirmation flow
  - chart rendering with mocked data


## 11) MVP Scope Checklist

**Must-have (MVP)**
- Add product by URL
- Manual entry of title + price
- Preview fetch for supported domains (optional, user-initiated)
- Dashboard list view
- Product detail view with history chart
- Date range presets
- Delete with confirmation
- Manual refresh (same-day overwrite)
- Dockerized deployment with Postgres

**Nice-to-have (v1.1)**
- Search/filter/sort enhancements
- Better canonical URL handling
- Failure tracking + retries

**Future (v2)**
- Price drop alerts
- Accounts + auth
- Import/export tracked list
- Tags/lists (e.g., “Wishlist”, “Shoes”, “Gym”)
- Shareable views


## 12) Edge Cases & Error Handling
- Invalid URL format
- Price not found / currency missing
- Preview fetch unsupported or blocked domains
- Duplicate product URL added again (reject or de-dupe)
- Variant-specific pricing (size/color changes price)
