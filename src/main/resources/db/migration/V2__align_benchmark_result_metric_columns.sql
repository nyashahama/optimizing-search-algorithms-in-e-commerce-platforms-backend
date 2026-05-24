ALTER TABLE benchmark_results ADD COLUMN IF NOT EXISTS precision_atk DOUBLE PRECISION;
UPDATE benchmark_results SET precision_atk = precision_at_k WHERE precision_atk IS NULL AND precision_at_k IS NOT NULL;
ALTER TABLE benchmark_results DROP COLUMN IF EXISTS precision_at_k;

ALTER TABLE benchmark_results ADD COLUMN IF NOT EXISTS recall_atk DOUBLE PRECISION;
UPDATE benchmark_results SET recall_atk = recall_at_k WHERE recall_atk IS NULL AND recall_at_k IS NOT NULL;
ALTER TABLE benchmark_results DROP COLUMN IF EXISTS recall_at_k;

ALTER TABLE benchmark_results ADD COLUMN IF NOT EXISTS mrr_atk DOUBLE PRECISION;
UPDATE benchmark_results SET mrr_atk = mrr_at_k WHERE mrr_atk IS NULL AND mrr_at_k IS NOT NULL;
ALTER TABLE benchmark_results DROP COLUMN IF EXISTS mrr_at_k;

ALTER TABLE benchmark_results ADD COLUMN IF NOT EXISTS ndcg_atk DOUBLE PRECISION;
UPDATE benchmark_results SET ndcg_atk = ndcg_at_k WHERE ndcg_atk IS NULL AND ndcg_at_k IS NOT NULL;
ALTER TABLE benchmark_results DROP COLUMN IF EXISTS ndcg_at_k;
