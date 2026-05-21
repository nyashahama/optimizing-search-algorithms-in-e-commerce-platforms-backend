# Phase 4: Benchmarking

## Goal

Add reproducible benchmark runs that compare search engines using query sets, relevance judgments, metrics, and reports.

- [x] Add query set, query, and judgment models and repositories.
- [x] Add benchmark run/result models and repository.
- [x] Add benchmark start/status/results endpoints.
- [x] Capture latency, result count, returned IDs, precision/recall/MRR/nDCG metrics.
- [x] Add benchmark execution timing percentiles (p50/p95/p99), throughput metrics, and freshness deltas.
- [x] Persist benchmark report artifacts (`results.json`, `latency.csv`, `relevance.csv`) to filesystem under `reports/<timestamp>/`.
- [x] Add deterministic sample dataset/fixtures for benchmark query sets and judgments.
- [x] Add CSV/JSON report endpoints in addition to the markdown report.
