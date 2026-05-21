package com.nyasha.store.benchmark.dtos;

public record BenchmarkRunRequest(
        Long querySetId,
        Integer limit
) {
}
