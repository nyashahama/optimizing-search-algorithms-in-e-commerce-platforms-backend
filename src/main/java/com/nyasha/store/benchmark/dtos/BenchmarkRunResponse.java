package com.nyasha.store.benchmark.dtos;

public record BenchmarkRunResponse(
        Long runId,
        String status,
        int queryCount
) {
}
