package com.nyasha.store.observability;

import com.nyasha.store.benchmark.repositories.BenchmarkRunRepository;
import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import com.nyasha.store.search.OpenSearchSearchClient;
import org.junit.jupiter.api.Test;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalStatusServiceTest {

    private final BenchmarkRunRepository benchmarkRunRepository = mock(BenchmarkRunRepository.class);
    private final IndexManagementService indexManagementService = mock(IndexManagementService.class);
    private final OpenSearchSearchClient openSearchSearchClient = mock(OpenSearchSearchClient.class);
    private final OperationalStatusService operationalStatusService = new OperationalStatusService(
            benchmarkRunRepository,
            indexManagementService,
            openSearchSearchClient
    );

    @Test
    void returnsDegradedWhenIndexingBackpressureDetected() {
        IndexingStatusSnapshot indexingStatus = new IndexingStatusSnapshot(
                12L,
                60L,
                0L,
                0L,
                15_000L,
                true,
                "2026-05-22T00:00:00Z"
        );

        when(indexManagementService.getStatus()).thenReturn(indexingStatus);
        when(openSearchSearchClient.clusterHealthStatus()).thenReturn("yellow");
        when(openSearchSearchClient.isHealthy()).thenReturn(true);
        when(benchmarkRunRepository.countByStatus(BenchmarkRunStatus.QUEUED)).thenReturn(0L);
        when(benchmarkRunRepository.countByStatus(BenchmarkRunStatus.RUNNING)).thenReturn(1L);
        when(benchmarkRunRepository.countByStatus(BenchmarkRunStatus.FAILED)).thenReturn(0L);

        OperationalStatusSnapshot status = operationalStatusService.getStatus();

        assertThat(status.overallStatus()).isEqualTo("DEGRADED");
        assertThat(status.activeWarnings())
                .containsExactly("Indexing backlog is in sustained backpressure state");
    }

    @Test
    void returnsDownWhenSearchClusterUnavailable() {
        IndexingStatusSnapshot indexingStatus = new IndexingStatusSnapshot(12L, 0L, 0L, 0L, 0L, false, "never");

        when(indexManagementService.getStatus()).thenReturn(indexingStatus);
        when(openSearchSearchClient.clusterHealthStatus()).thenReturn("unreachable");
        when(openSearchSearchClient.isHealthy()).thenReturn(false);
        when(benchmarkRunRepository.countByStatus(BenchmarkRunStatus.QUEUED)).thenReturn(0L);
        when(benchmarkRunRepository.countByStatus(BenchmarkRunStatus.RUNNING)).thenReturn(0L);
        when(benchmarkRunRepository.countByStatus(BenchmarkRunStatus.FAILED)).thenReturn(0L);

        OperationalStatusSnapshot status = operationalStatusService.getStatus();

        assertThat(status.overallStatus()).isEqualTo("DOWN");
        assertThat(status.openSearchHealthy()).isFalse();
        assertThat(status.activeWarnings()).containsExactly("Search cluster is not available");
    }
}
