# Price Tracker Website — Requirements Document (v0.1)

## 1) Overview

### 1.1 Purpose
Build a web application that allows a user to **track product prices across multiple retail websites** by submitting product links. The app collects and stores each product’s price over time and displays **price history graphs** with filtering options.

### 1.2 Goals
- Track products from multiple websites (Amazon, Lululemon, Aritzia, Jimmy Choo, etc.).
- Automatically fetch:
  - Product title/name
  - Current price
  - (Optionally) currency and availability status
- Display:
  - “All tracked items” overview page
  - Individual product detail page with **price history chart**
- Allow management:
  - Add product by URL
  - Delete product (with confirmation)
- Deployable via **Docker/Podman** with reliable persistence

### 1.3 Non-Goals (for initial version)
- Price drop alerts (email/push)
- Multi-user accounts / authentication (unless explicitly needed)
- Browser extension integration
- Full internationalization (multi-language)
- Complex anti-bot bypass / CAPTCHA solving


## 2) Users & Use Cases

### 2.1 Primary User
- Single user (you), tracking personal shopping items across different websites.

### 2.2 Core Use Cases
1. **Add Product**
   - User pastes URL → app fetches title + current price → saves product and initial price point.
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
  - Fetch product metadata:
    - `title`
    - `price` (numeric)
    - `currency` (e.g., CAD/USD; if available)
  - Store product record
  - Store initial price point in price history

**Acceptance Criteria:**
- If metadata fetch succeeds, product appears in the dashboard immediately.
- If fetch fails, show a helpful error (e.g., “could not parse price from this site”).

#### FR-2: Support multiple retailer domains
**Requirements:**
- System should support different parsing strategies depending on domain:
  - Amazon, Lululemon, Aritzia, etc.
- Design should allow adding new parsers without rewriting everything (plugin-style).

**Acceptance Criteria:**
- Parsers can be extended by adding a new handler keyed by domain pattern.


### 3.2 Price History

#### FR-3: Record price snapshots over time
**Requirements:**
- Each tracked product should have a history of price records:
  - timestamp
  - price
  - currency
  - optional metadata (availability, raw page hash, parser version)

**Acceptance Criteria:**
- At minimum, adding a product creates 1 data point.
- System supports repeated snapshot insertions later.

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


### 3.5 Data Fetching Behavior (MVP vs Next Step)

#### FR-8: Manual refresh (MVP)
**Requirements:**
- Provide a “Refresh price” button per product or on dashboard.
- On refresh:
  - fetch current price
  - append new price point

**Acceptance Criteria:**
- Refresh adds a new record with a new timestamp.

#### FR-9: Automatic scheduled refresh (recommended next step)
**Requirements:**
- Background job runs periodically (e.g., every 6h / daily)
- Fetches latest prices for all products
- Rate limiting per domain to avoid bans

**Acceptance Criteria:**
- Job runs reliably and logs successes/failures.
- Avoids hammering a single retailer.


## 4) Non-Functional Requirements

### 4.1 Performance
- Dashboard should load in < 1s for ~100 products (local deployment).
- Price history graph query should return in < 300ms for typical ranges.

### 4.2 Reliability & Data Integrity
- Price records are append-only (no overwrites).
- Deleting product removes all dependent data (cascading delete).
- DB schema supports migration versioning.

### 4.3 Security (Important)
**Key requirements:**
- Prevent SSRF:
  - Only allow `http`/`https`
  - Block private IP ranges and localhost targets
  - Validate domain and DNS resolution defensively
- Input validation:
  - URL length limits
  - Store normalized canonical URLs
- Secure headers:
  - Content Security Policy (CSP)
  - X-Frame-Options / frame-ancestors
  - Strict-Transport-Security (if deployed publicly)
- Backend secrets:
  - Stored only in environment variables (no hardcoding)
- Rate limiting:
  - Prevent abuse of the “fetch price” endpoint
- Logging:
  - Never log sensitive headers/cookies

### 4.4 Maintainability / Extensibility
- Retailer parsing should be modular and testable.
- Clear separation between:
  - parsing layer
  - persistence layer
  - API layer
  - UI layer


## 5) Suggested Technical Architecture

### 5.1 Frontend (Rspack + React + TypeScript + Tailwind CSS)
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
- Scraping/parsing service for product metadata fetch
- Background scheduler (optional in v1)

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
- `source` (text, e.g., “manual”, “scheduler”)
- `raw_price_text` (text, optional for debugging)
- Index: `(product_id, checked_at desc)`


## 6) API Requirements (REST)

### 6.1 Add product
`POST /api/products`
```json
{ "url": "https://..." }
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

### 6.2 List products
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

### 6.3 Get single product
`GET /api/products/:id`

### 6.4 Get price history (with range filters)
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

### 6.5 Refresh price manually
`POST /api/products/:id/refresh`

### 6.6 Delete product
`DELETE /api/products/:id`


## 7) Price Fetching / Parsing Strategy

### 7.1 Approach
- Identify retailer by `domain`
- Use domain-specific parser function:
  - Extract title
  - Extract price
- Store results in DB

### 7.2 Parsing methods (priority order)
1. Retailer-specific DOM parsing (HTML parsing with selectors)
2. Structured data parsing:
   - JSON-LD (schema.org Product/Offer)
   - OpenGraph metadata (title fallback)
3. Fallback heuristics (last-resort regex scanning)

### 7.3 Anti-bot considerations
Some sites will block automated requests.
- MVP target: “best effort” for supported sites
- Future options:
  - Headless browser fetch
  - Rotating user agent + respectful rate limiting
  - Caching + conditional fetches


## 8) Deployment Requirements (Docker/Podman)

### 8.1 Containerization
- One container for backend
- One container for frontend
- One container for PostgreSQL
- Compose file for local dev: `docker-compose.yml` (or Podman equivalent)

### 8.2 Environment Variables
Backend:
- `DATABASE_URL`
- `PORT`
- `LOG_LEVEL`
- `ALLOWED_DOMAINS` (optional allowlist)
- `FETCH_TIMEOUT_MS`
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
- parser used
- parse errors with minimal context (avoid logging secrets)

### 9.2 Metrics (optional)
- number of tracked products
- average fetch duration
- per-domain failure rate


## 10) Testing Requirements

### 10.1 Backend tests
- Parser unit tests using saved HTML fixtures
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
- Parse title + price (for supported sites)
- Dashboard list view
- Product detail view with history chart
- Date range presets
- Delete with confirmation
- Manual refresh
- Dockerized deployment with Postgres

**Nice-to-have (v1.1)**
- Auto scheduled refresh
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
- URL resolves to non-product page
- Price not found / currency missing
- Retailer blocks request (403 / CAPTCHA)
- Product page changes DOM structure
- Duplicate product URL added again (reject or de-dupe)
- Variant-specific pricing (size/color changes price)
