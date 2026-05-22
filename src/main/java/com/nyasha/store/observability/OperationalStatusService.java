package com.nyasha.store.observability;

import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.repositories.BenchmarkRunRepository;
import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import com.nyasha.store.search.OpenSearchSearchClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationalStatusService {

    private static final int CRITICAL_INDEX_FAILURE_THRESHOLD = 1;
    private static final int HIGH_BENCHMARK_CONCURRENCY_WARNING_THRESHOLD = 2;

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final IndexManagementService indexManagementService;
    private final OpenSearchSearchClient openSearchSearchClient;

    public OperationalStatusService(
            BenchmarkRunRepository benchmarkRunRepository,
            IndexManagementService indexManagementService,
            OpenSearchSearchClient openSearchSearchClient
    ) {
        this.benchmarkRunRepository = benchmarkRunRepository;
        this.indexManagementService = indexManagementService;
        this.openSearchSearchClient = openSearchSearchClient;
    }

    public OperationalStatusSnapshot getStatus() {
        IndexingStatusSnapshot indexingStatus = indexManagementService.getStatus();
        String openSearchHealth = openSearchSearchClient.clusterHealthStatus();
        boolean openSearchHealthy = openSearchSearchClient.isHealthy();

        long queuedBenchmarkRuns = benchmarkRunRepository.countByStatus(BenchmarkRunStatus.QUEUED);
        long runningBenchmarkRuns = benchmarkRunRepository.countByStatus(BenchmarkRunStatus.RUNNING);
        long failedBenchmarkRuns = benchmarkRunRepository.countByStatus(BenchmarkRunStatus.FAILED);

        List<String> activeWarnings = new ArrayList<>();

        if (!openSearchHealthy) {
            activeWarnings.add("Search cluster is not available");
        }
        if (indexingStatus.sustainedBackpressure()) {
            activeWarnings.add("Indexing backlog is in sustained backpressure state");
        }
        if (indexingStatus.failedEvents() >= CRITICAL_INDEX_FAILURE_THRESHOLD) {
            activeWarnings.add("Indexing has failed events requiring manual attention");
        }
        if (indexingStatus.deadLetterEvents() >= CRITICAL_INDEX_FAILURE_THRESHOLD) {
            activeWarnings.add("Indexing dead-letter events are present");
        }
        if (runningBenchmarkRuns + queuedBenchmarkRuns > HIGH_BENCHMARK_CONCURRENCY_WARNING_THRESHOLD) {
            activeWarnings.add("Benchmark queue backlog may be high");
        }
        if (failedBenchmarkRuns > 0) {
            activeWarnings.add("Recent benchmark failures should be investigated");
        }

        String overallStatus = activeWarnings.isEmpty() ? "UP" : (openSearchHealthy ? "DEGRADED" : "DOWN");

        return new OperationalStatusSnapshot(
                overallStatus,
                LocalDateTime.now(ZoneOffset.UTC).toString(),
                openSearchHealth,
                openSearchHealthy,
                indexingStatus.openSearchDocumentCount(),
                indexingStatus.pendingEvents(),
                indexingStatus.failedEvents(),
                indexingStatus.deadLetterEvents(),
                indexingStatus.sustainedBackpressure(),
                indexingStatus.lastSuccessfulIndexTime(),
                queuedBenchmarkRuns,
                runningBenchmarkRuns,
                failedBenchmarkRuns,
                activeWarnings
        );
    }
}
