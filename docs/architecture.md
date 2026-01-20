# Architecture Notes

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
- FETCH_TIMEOUT_MS
- RATE_LIMIT_PER_MINUTE

Frontend:
- API_BASE_URL
