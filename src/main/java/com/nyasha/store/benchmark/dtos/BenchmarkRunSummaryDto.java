package com.nyasha.store.benchmark.dtos;

import com.nyasha.store.benchmark.models.BenchmarkRunStatus;

import java.time.LocalDateTime;

public record BenchmarkRunSummaryDto(
        Long runId,
        BenchmarkRunStatus status,
        String querySetName,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer totalQueries,
        Integer totalEngines,
        String reportUrl,
        String reportJsonUrl,
        String latencyCsvUrl,
        String relevanceCsvUrl,
        String reportDirectory,
        Long durationMs,
        Double throughputQueriesPerSecond,
        Long latencyMinMs,
        Long latencyP50Ms,
        Long latencyP95Ms,
        Long latencyP99Ms,
        Double latencyAvgMs,
        Long freshnessP50Ms,
        Long freshnessP95Ms,
        Long freshnessP99Ms,
        Double freshnessAvgMs
) {
}
