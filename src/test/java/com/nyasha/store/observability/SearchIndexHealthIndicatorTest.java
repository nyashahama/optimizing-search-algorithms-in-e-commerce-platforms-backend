package com.nyasha.store.observability;

import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import com.nyasha.store.observability.health.SearchIndexHealthIndicator;
import com.nyasha.store.search.OpenSearchSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchIndexHealthIndicatorTest {

    @Test
    void healthyWhenDependenciesReportHealthyState() {
        IndexManagementService indexManagementService = mock(IndexManagementService.class);
        OpenSearchSearchClient openSearchSearchClient = mock(OpenSearchSearchClient.class);
        SearchIndexHealthIndicator indicator = new SearchIndexHealthIndicator(openSearchSearchClient, indexManagementService);

        when(openSearchSearchClient.clusterHealthStatus()).thenReturn("yellow");
        when(openSearchSearchClient.isHealthy()).thenReturn(true);
        when(indexManagementService.getStatus()).thenReturn(
                new IndexingStatusSnapshot(5L, 0L, 0L, 0L, 0L, false, "2026-05-22T00:00:00Z")
        );

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("openSearchClusterHealth", "yellow");
    }

    @Test
    void downWhenOpenSearchUnavailable() {
        IndexManagementService indexManagementService = mock(IndexManagementService.class);
        OpenSearchSearchClient openSearchSearchClient = mock(OpenSearchSearchClient.class);
        SearchIndexHealthIndicator indicator = new SearchIndexHealthIndicator(openSearchSearchClient, indexManagementService);

        when(openSearchSearchClient.clusterHealthStatus()).thenReturn("unreachable");
        when(openSearchSearchClient.isHealthy()).thenReturn(false);
        when(indexManagementService.getStatus()).thenReturn(
                new IndexingStatusSnapshot(0L, 0L, 0L, 0L, 0L, false, "never")
        );

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsKey("openSearchClusterHealth");
    }
}
