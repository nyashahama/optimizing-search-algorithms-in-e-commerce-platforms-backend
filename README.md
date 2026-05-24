# E-commerce Search Optimization Backend

This project is being rebuilt into a production-style search benchmarking lab using e-commerce catalog data.

## Goal

Compare search approaches across latency, indexing throughput, freshness, and relevance:

1. SQL LIKE
2. PostgreSQL full-text search
3. Custom in-memory inverted index
4. OpenSearch BM25

2. Keep endpoint behavior consistent and auditable across all e-commerce surfaces (auth, request shape, and status semantics).

## Endpoints by Domain

- **Authentication and accounts**: `POST /users/register`, `POST /users/login`, `GET/PUT/DELETE /users`
- **Catalog and search**: `GET /api/products`, `GET /api/products/{id}`, `POST/PUT/DELETE /api/products`, `GET /api/categories`, `GET /api/search`, `GET /api/search/compare`
- **Wishlist**: `GET /api/wishlists/me`, `POST /api/wishlists/me/items`, `DELETE /api/wishlists/me/items/{itemId}`
- **Cart and wishlist**: `GET /api/carts/me`, `POST /api/carts/me/items`, `PATCH /api/carts/me/items/{itemId}`, `DELETE /api/carts/me/items/{itemId}`, `DELETE /api/carts/me`
- **Checkout and orders**: `POST /api/checkouts/preview`, `POST /api/checkouts/confirm`, `GET /api/orders/me`, `POST /api/orders/{id}/pack|ship|delivered|cancel`
- **Returns and reviews**: `POST /api/returns/{orderId}`, `POST /api/returns/{returnId}/approve|reject|refund`, `POST /api/reviews`, `GET /api/reviews/products/{productId}`
- **Inventory and suppliers**: `GET /api/inventory/{productId}`, `PUT /api/inventory/{productId}`, `PATCH /api/inventory/{productId}/adjust`, `GET /api/inventory/low-stock`, `GET /api/suppliers`
- **Payments and addresses**: `GET /api/payments/orders/{orderId}`, `POST /api/payments/orders/{orderId}/capture|refund`, `POST/GET/PUT/DELETE /api/addresses/me`
- **Benchmark and operations**: `POST /api/benchmarks/runs`, `GET /api/benchmarks/runs/{id}`, `GET /api/benchmarks/runs/{id}/artifacts/*`, `POST /api/index/rebuild`, `GET /api/ops/status`
- **API readiness**: The endpoint list above is enforced by `EndpointAuthorizationMatrixTest` and compared in `EndpointDocumentationAlignmentTest`, so endpoint additions require explicit contract updates.

For storefront behavior, read-only catalog/search endpoints are intentionally public (`/api/products`, `/api/categories`, `/api/search`, and `/api/reviews/products/{productId}`), while shopping, checkout, and operational actions remain authenticated.

## Endpoint Contract for Ecommerce Adoption

This project is structured to be usable as a starter commerce backend:

- **Public discovery** routes for browse/search with no auth required.
- **Customer routes** for carts, checkout, addresses, orders, returns, reviews, and wishlists.
- **Administrative routes** for catalog, benchmark control, index control, user administration, and operational actions.

Route intent and auth behavior are validated in:

- `src/test/java/com/nyasha/store/configurations/EndpointAuthorizationMatrixTest.java`

This keeps the API surface explicit for teams expecting predictable security behavior before adding storefront or storefront-adjacent integrations.
## Project Status

Current phase: **Phase 7 - Observability And Operational Playbooks**
Next phase: **Project Complete**

Phase 0 made the existing Spring Boot backend buildable, testable, documented, and safe enough to support the search benchmarking lab. Phase 1 added a common search abstraction so search engines can be compared through one API. Phase 2 added infrastructure dependencies for local development. Phase 3 added event-driven indexing and OpenSearch synchronization. Phase 4 implemented benchmark execution, metrics, and report artifacts. Phase 5 added completion docs, repeatable runbook, and verification guidance.

## Phase Roadmap

| Phase | Name | Status |
| --- | --- | --- |
| Phase 0 | Stabilize Current Backend | Complete |
| Phase 1 | Search Abstraction | Complete |
| Phase 2 | Infrastructure | Complete |
| Phase 3 | Event-Driven Indexing | Complete |
| Phase 4 | Benchmarking | Complete |
| Phase 5 | Verification And Documentation | Complete |
| Phase 6 | Commerce Operations Hardening | Complete |
| Phase 7 | Observability And Operational Playbooks | Complete |

## Tech Stack

- Java 21
- Spring Boot 3.4.3
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- H2 for tests
- Maven Wrapper

## Prerequisites

```bash
java -version
```

Expected: Java 21.

## Run Tests

```bash
./mvnw test
```

## Search API

- `GET /api/search?q=wireless&engine=in_memory&limit=20` runs a single search engine.
- `GET /api/search/compare?q=wireless&limit=20` runs all search engines side-by-side.
- `GET /api/products/search?query=wireless` delegates to the in-memory engine for backward compatibility.
- `GET /api/inventory/{productId}?variantId=` fetches current inventory record for a product/variant.
- `PUT /api/inventory/{productId}?variantId=` creates or updates inventory quantity, location, and reorder threshold.
- `PATCH /api/inventory/{productId}/adjust?variantId=` increments/decrements inventory stock atomically.
- `GET /api/inventory/low-stock` lists inventory rows at or below reorder threshold.
- `GET /api/suppliers` and `GET /api/suppliers/{supplierId}` read supplier records.
- `POST /api/suppliers`, `PUT /api/suppliers/{supplierId}`, `DELETE /api/suppliers/{supplierId}` manage suppliers.
- `POST /api/index/rebuild` rebuilds the OpenSearch index.
- `GET /api/index/status` reports document count plus event queue health including backpressure indicators.
- `GET /api/payments/orders/{orderId}` returns payment details (order owner or admin).
- `POST /api/payments/orders/{orderId}/capture` captures a pending payment.
- `POST /api/payments/orders/{orderId}/refund` refunds a captured payment.
- `POST /api/benchmarks/runs` starts a benchmark run and returns `202 Accepted` with status `QUEUED`.
- `GET /api/benchmarks/runs/{runId}` returns run status and summary.
- `GET /api/benchmarks/runs/{runId}/results` returns benchmark row results.
- `GET /api/benchmarks/runs/{runId}/report.md` downloads a markdown benchmark report.
- `GET /api/benchmarks/runs/{runId}/report.json` returns JSON report payload.
- `GET /api/benchmarks/runs/{runId}/latency.csv` returns per-query latency rows.
- `GET /api/benchmarks/runs/{runId}/relevance.csv` returns per-query relevance metrics.

## Run The API Locally

### With local Docker services (recommended for phase 2)

```bash
cp .env.example .env
docker compose up --build -d
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run
```

### Without Docker

Set PostgreSQL connection values if your local database differs from the defaults:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/store_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=change-me
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

Benchmark execution is asynchronous:
- `POST /api/benchmarks/runs` returns quickly with `status=QUEUED`.
- Poll `GET /api/benchmarks/runs/{runId}` until `COMPLETED` or `FAILED`.

## Benchmark Dataset Seeding and Startup

Benchmark query sets and judgments are seeded automatically from:

- `src/main/resources/benchmark/seed/benchmark-fixtures.json`

On first startup, if no query sets exist, the service creates an `electronics-basic` fixture with deterministic queries and judgments.

The seed can be refreshed by truncating benchmark tables before startup:

```sql
TRUNCATE TABLE benchmark_judgments, benchmark_queries, benchmark_query_sets CASCADE;
```

Then restart the app and query set creation runs again.

## Local Verification Runbook

Use this sequence for a deterministic end-to-end check:

1. Start local dependencies:

```bash
cp .env.example .env
docker compose up -d
set -a
source .env
set +a
```

2. Start the API (services include the application container now):

```bash
docker compose up --build -d
```

3. Run benchmark on the seeded dataset:

```bash
curl -i -s -X POST "http://localhost:8080/api/benchmarks/runs" \
  -H "Content-Type: application/json" \
  -d '{"limit":20}'
```

4. Capture the returned run id and check summary:

```bash
RUN_ID=$(curl -s -X POST "http://localhost:8080/api/benchmarks/runs" \
  -H "Content-Type: application/json" \
  -d '{"limit":20}' | jq '.runId')
curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}"
```

Wait for `status` to become `COMPLETED` before reading the report:

```bash
until curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}" | jq -r '.status' \
  | grep -q 'COMPLETED\|FAILED'; do
  sleep 1
done

curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}/report.md"
```

Expected output includes: `status`, `throughputQueriesPerSecond`, `latencyP50Ms`, `latencyP95Ms`, `latencyP99Ms`, `freshnessP50Ms`.

5. Fetch benchmark artifacts and verify files exist:

```bash
curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}/report.md"
curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}/report.json"
curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}/latency.csv"
curl -s "http://localhost:8080/api/benchmarks/runs/${RUN_ID}/relevance.csv"
ls -R reports
```

### Rate Limiting

- Configure request-rate controls with:
  - `SECURITY_RATE_LIMIT_ENABLED` (`true`/`false`)
  - `SECURITY_RATE_LIMIT_REQUESTS_PER_MINUTE` (default: `120`)
  - `SECURITY_RATE_LIMIT_WINDOW_MS` (default: `60000`)
- Exceeded limits return `429 Too Many Requests` with `Retry-After`.

## Metrics Interpretation

### Search metrics

- `latencyP50Ms`, `latencyP95Ms`, `latencyP99Ms`: percentiles computed on successful searches.
- `throughputQueriesPerSecond`: total benchmark queries executed per second for the full run.

### Freshness metrics

- `freshnessP50Ms`, `freshnessP95Ms`, `freshnessP99Ms`: processing lag between indexing event creation and completion.
- Lower values indicate better indexing freshness and lower event lag.

### Relevance metrics

- `precisionAtK`, `recallAtK`, `mrrAtK`, `ndcgAtK` are computed against seeded judgments and help compare relevance quality.

## Troubleshooting

## Operational Controls

### Runtime status endpoint

The operational endpoint gives an ops-focused single response with dependency status and current backlog:

```bash
curl -s http://localhost:8080/api/ops/status
```

Response highlights:

- `overallStatus`: `UP`, `DEGRADED`, or `DOWN`
- `openSearchClusterHealth`: `green`, `yellow`, `red`, or `unreachable`
- `pendingIndexEvents`, `failedIndexEvents`, `deadLetterIndexEvents`
- `queuedBenchmarkRuns`, `runningBenchmarkRuns`, `failedBenchmarkRuns`
- `activeWarnings`

### Request correlation

All API responses include `X-Request-Id`:

- If the client sends `X-Request-Id`, that value is preserved.
- If omitted, the system generates a request id and returns it in the response.

This id is written to logs as `requestId` in the console format.

### PostgreSQL connectivity errors

If startup fails with connection errors, confirm:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/store_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=change-me
```

Then restart the app.

If port `5432` is already in use locally, set `POSTGRES_PORT` and update the JDBC URL before starting Compose:

```bash
POSTGRES_PORT=55432
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/store_db
```

### Kafka consumer/producer errors

If indexing events are not being consumed, verify `SPRING_KAFKA_BOOTSTRAP_SERVERS` points at a running broker and topic auto-create is enabled (default in Compose).

### OpenSearch failures in search/indexing

If `/api/search/compare` shows OpenSearch errors, check OpenSearch health and that the index can be created:

```bash
curl -s "http://localhost:9200/_cluster/health?pretty"
```

If you need to ignore OpenSearch temporarily, benchmarking still proceeds for other engines.

## Design Notes

Design and execution notes are maintained locally and intentionally not tracked in this repository.
