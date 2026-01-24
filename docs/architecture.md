# Architecture Notes

## Deployment model
- Local-only on a Macbook; services bind to localhost by default
- No public ingress or internet-exposed ports required
- Do not open firewall rules, port-forwarding, or public tunnels

## Persistence choice
- Database: PostgreSQL 16
- Rationale: strong relational model, time-series friendly indexing, standard Docker support

## Migration tooling
- Tool: Migratus
- Strategy: SQL-based migrations stored in `backend/resources/migrations`
- Rationale: lightweight, Clojure-native, and easy to run in CI/local dev

## Environment variables
Backend:
- DATABASE_URL
- PORT
- LOG_LEVEL
- ALLOWED_DOMAINS
- DENYLIST_CONFIG
- RATE_LIMIT_PER_MINUTE

Frontend:
- API_BASE_URL

## Time semantics
- All persisted timestamps are UTC (timestamptz).
- UI displays timestamps in Eastern Time (America/New_York) with DST handling.
- Price snapshots are unique per product per UTC day (same-day overwrites).
