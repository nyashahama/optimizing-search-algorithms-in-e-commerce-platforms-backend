# Phase 3: Event-Driven Indexing

## Goal

Publish catalog mutations to Kafka and keep OpenSearch synchronized asynchronously through a worker process.

- [x] Add indexing event model with event time, processed time, status, and retry count.
- [x] Publish `UPSERT` and `DELETE` events from product write flow.
- [x] Add Kafka consumer for replay-safe index sync.
- [x] Add OpenSearch indexing service for document create/update/delete.
- [x] Add index rebuild and index status endpoints.
- [x] Add alerting/monitoring for sustained queue backpressure.
- [x] Add producer retry policy and dead-letter strategy for permanently failed events.
