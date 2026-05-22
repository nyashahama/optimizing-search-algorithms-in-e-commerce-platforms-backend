package com.nyasha.store.observability;

import java.util.List;

public record OperationalStatusSnapshot(
        String overallStatus,
        String timestampUtc,
        String openSearchClusterHealth,
        boolean openSearchHealthy,
        long openSearchDocumentCount,
        long pendingIndexEvents,
        long failedIndexEvents,
        long deadLetterIndexEvents,
        boolean sustainedIndexBackpressure,
        String lastSuccessfulIndexTime,
        long queuedBenchmarkRuns,
        long runningBenchmarkRuns,
        long failedBenchmarkRuns,
        List<String> activeWarnings
) {
}
