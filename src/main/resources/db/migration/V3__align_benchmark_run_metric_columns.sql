ALTER TABLE benchmark_runs ADD COLUMN IF NOT EXISTS latencyp50ms BIGINT;
UPDATE benchmark_runs SET latencyp50ms = latency_p50_ms WHERE latencyp50ms IS NULL AND latency_p50_ms IS NOT NULL;
ALTER TABLE benchmark_runs DROP COLUMN IF EXISTS latency_p50_ms;

ALTER TABLE benchmark_runs ADD COLUMN IF NOT EXISTS latencyp95ms BIGINT;
UPDATE benchmark_runs SET latencyp95ms = latency_p95_ms WHERE latencyp95ms IS NULL AND latency_p95_ms IS NOT NULL;
ALTER TABLE benchmark_runs DROP COLUMN IF EXISTS latency_p95_ms;

ALTER TABLE benchmark_runs ADD COLUMN IF NOT EXISTS latencyp99ms BIGINT;
UPDATE benchmark_runs SET latencyp99ms = latency_p99_ms WHERE latencyp99ms IS NULL AND latency_p99_ms IS NOT NULL;
ALTER TABLE benchmark_runs DROP COLUMN IF EXISTS latency_p99_ms;

ALTER TABLE benchmark_runs ADD COLUMN IF NOT EXISTS freshnessp50ms BIGINT;
UPDATE benchmark_runs SET freshnessp50ms = freshness_p50_ms WHERE freshnessp50ms IS NULL AND freshness_p50_ms IS NOT NULL;
ALTER TABLE benchmark_runs DROP COLUMN IF EXISTS freshness_p50_ms;

ALTER TABLE benchmark_runs ADD COLUMN IF NOT EXISTS freshnessp95ms BIGINT;
UPDATE benchmark_runs SET freshnessp95ms = freshness_p95_ms WHERE freshnessp95ms IS NULL AND freshness_p95_ms IS NOT NULL;
ALTER TABLE benchmark_runs DROP COLUMN IF EXISTS freshness_p95_ms;

ALTER TABLE benchmark_runs ADD COLUMN IF NOT EXISTS freshnessp99ms BIGINT;
UPDATE benchmark_runs SET freshnessp99ms = freshness_p99_ms WHERE freshnessp99ms IS NULL AND freshness_p99_ms IS NOT NULL;
ALTER TABLE benchmark_runs DROP COLUMN IF EXISTS freshness_p99_ms;
