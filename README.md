# Optimizing Search Algorithms In E-commerce Platforms - Backend

This project is being rebuilt into a production-style search benchmarking lab using e-commerce catalog data.

## Goal

Compare search approaches across latency, indexing throughput, freshness, and relevance:

1. SQL LIKE
2. PostgreSQL full-text search
3. Custom in-memory inverted index
4. OpenSearch BM25

## Current Phase

Current phase: **Phase 1 - Search Abstraction**

Phase 0 made the existing Spring Boot backend buildable, testable, documented, and safe enough to support the search benchmarking lab. Phase 1 adds a common search abstraction so search engines can be compared through one API.

## Phase Roadmap

| Phase | Name | Status |
| --- | --- | --- |
| Phase 0 | Stabilize Current Backend | Complete |
| Phase 1 | Search Abstraction | Current |
| Phase 2 | Infrastructure | Upcoming |
| Phase 3 | Event-Driven Indexing | Upcoming |
| Phase 4 | Benchmarking | Upcoming |
| Phase 5 | Verification And Documentation | Upcoming |

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

## Run The API Locally

Set PostgreSQL connection values if your local database differs from the defaults:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/store_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=Gyver
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

## Design Docs

- `docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md`
- `docs/superpowers/plans/2026-05-21-phase-0-stabilize-current-backend.md`
