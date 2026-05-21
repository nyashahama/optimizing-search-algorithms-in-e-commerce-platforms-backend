package com.nyasha.store.benchmark.dtos;

import com.nyasha.store.search.SearchEngineType;

public record BenchmarkResultDto(
        Long resultId,
        String queryText,
        SearchEngineType engine,
        long latencyMs,
        int resultCount,
        int returnedCount,
        String topResultProductIds,
        boolean supported,
        Double precisionAtK,
        Double recallAtK,
        Double mrrAtK,
        Double ndcgAtK,
        String errorMessage
) {
}

