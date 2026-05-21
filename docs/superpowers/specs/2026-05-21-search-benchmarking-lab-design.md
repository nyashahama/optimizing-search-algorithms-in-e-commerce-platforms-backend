# Search Benchmarking Lab Design

## Goal

Turn the backend into a production-style search benchmarking lab that uses e-commerce catalog data to compare search approaches across latency, indexing throughput, freshness, and relevance.

The project should remain Java/Spring Boot. Java is a good fit here because the goal is an enterprise-style backend with measurable infrastructure behavior, not only a quick search prototype.

## Current Phase

**Current phase: Phase 1 - Search Abstraction.**

Phase 0 stabilization is complete and verified. Phase 1 should add the common search abstraction so SQL LIKE, PostgreSQL full-text search, the in-memory index, and OpenSearch can be compared through one API.

## Phase Tracker

| Phase | Name | Purpose | Status |
| --- | --- | --- | --- |
| Phase 0 | Stabilize Current Backend | Ensure the existing Spring Boot app builds, current PR is clean, Java tooling is available, and baseline endpoints behave predictably. | Complete |
| Phase 1 | Search Abstraction | Add a common search interface so SQL LIKE, PostgreSQL full-text search, in-memory index, and OpenSearch can be compared through one API. | Current |
| Phase 2 | Infrastructure | Add Docker Compose for PostgreSQL, Kafka, OpenSearch, and local development profiles. | Upcoming |
| Phase 3 | Event-Driven Indexing | Publish product events to Kafka and consume them with an indexer worker that syncs OpenSearch asynchronously. | Upcoming |
| Phase 4 | Benchmarking | Add query sets, relevance judgments, benchmark runs, metric collection, and report generation. | Upcoming |
| Phase 5 | Verification And Documentation | Add seed data, repeatable benchmark commands, CI verification, README updates, architecture docs, and example reports. | Upcoming |

## Architecture

The backend will be a **Search Benchmarking Lab: Production Simulation**.

Core components:

- **Spring Boot API** owns catalog data, search APIs, benchmark APIs, and product write flows.
- **PostgreSQL** remains the source of truth for products, categories, benchmark fixtures, query sets, relevance judgments, benchmark runs, and benchmark results.
- **Kafka** carries product create/update/delete events and supports realistic asynchronous indexing behavior.
- **Indexer Worker** consumes Kafka product events and syncs OpenSearch. This can start as a worker inside the same Spring Boot application and later be split into a separate process if needed.
- **OpenSearch** provides the production-grade search backend under test.
- **Custom In-Memory Index** remains useful as a hand-built algorithm comparison target.
- **Benchmark Runner** executes repeatable suites against all search engines and stores results.
- **Reports** are generated as JSON, CSV, and Markdown so results are easy to review in GitHub.

## Search Engines Compared

The benchmark suite should compare these engines first:

1. **SQL LIKE**: the slow/simple baseline.
2. **PostgreSQL full-text search**: database-native improvement.
3. **Custom in-memory inverted index**: algorithm-focused implementation already aligned with the original project goal.
4. **OpenSearch BM25**: production-grade distributed search baseline.

Hybrid/vector search is explicitly out of scope for the first complete version. It can become a later advanced phase.

## Data Model Direction

Existing e-commerce entities can remain, but the search project needs richer product and benchmark models.

Product fields should support:

- `productId`
- `name`
- `description`
- `sku`
- `basePrice`
- `brand`
- `category`
- `attributes`
- `inventoryStatus`
- `createdAt`
- `updatedAt`

Search and benchmark models:

- `ProductSearchDocument`: denormalized document sent to OpenSearch.
- `SearchQuerySet`: named query groups such as `electronics-basic`, `typo-heavy`, and `category-filtered`.
- `SearchJudgment`: relevance labels for query/product pairs.
- `BenchmarkRun`: one execution of a benchmark suite.
- `BenchmarkResult`: per-engine/per-query latency, result count, top result IDs, relevance metrics, and errors.
- `IndexingEvent`: product event tracking with event time, processed time, status, and retry count.

## API Direction

Catalog and search APIs:

- `POST /api/products`: create product and publish indexing event.
- `PUT /api/products/{id}`: update source data and publish indexing event.
- `DELETE /api/products/{id}`: delete source data and publish deletion event.
- `GET /api/search?engine=opensearch&q=...`: run one search engine.
- `GET /api/search/compare?q=...`: compare all engines side by side.
- `GET /api/search/autocomplete?q=...`: return OpenSearch-backed suggestions.

Benchmark APIs:

- `POST /api/benchmarks/runs`: start a benchmark run.
- `GET /api/benchmarks/runs/{id}`: get run status and summary.
- `GET /api/benchmarks/runs/{id}/results`: get detailed benchmark results.
- `GET /api/benchmarks/runs/{id}/report.md`: return Markdown report.
- `POST /api/index/rebuild`: rebuild all search indexes.
- `GET /api/index/status`: show OpenSearch document counts, Kafka lag/freshness, and last successful indexing time.

## Metrics

The project should prove production readiness using these metrics:

- Search latency: p50, p95, p99, minimum, maximum, and average latency per engine.
- Indexing throughput: products/events indexed per second.
- Freshness lag: time between product write event and searchable OpenSearch document.
- Relevance: precision@k, recall@k, MRR, and nDCG where judgments exist.
- Error rate: failed searches, failed index events, retry counts.

## Reporting

The first complete version should prioritize reproducible reports over a dashboard.

Reports should be written under `reports/<timestamp>/`:

- `summary.md`: human-readable benchmark summary.
- `results.json`: complete machine-readable results.
- `latency.csv`: per-query/per-engine latency rows.
- `relevance.csv`: per-query/per-engine relevance metrics.

## Testing Strategy

Tests should scale with each phase:

- Unit tests for query normalization, search result mapping, index update logic, and report generation.
- Repository tests for SQL LIKE and PostgreSQL full-text search queries.
- Integration tests using Testcontainers for PostgreSQL, Kafka, and OpenSearch once infrastructure is added.
- Benchmark smoke tests that run a small query set and produce deterministic report files.

## Completion Criteria

The backend is considered complete when:

- The application starts locally with one command after dependencies are available.
- Products can be created, updated, deleted, searched, and compared across engines.
- Product writes flow through Kafka and become searchable in OpenSearch.
- Benchmark runs produce JSON, CSV, and Markdown reports.
- At least one seed dataset and one query judgment set are included.
- Documentation explains setup, architecture, benchmark commands, and how to interpret results.
- CI or documented local verification runs the test suite successfully.
