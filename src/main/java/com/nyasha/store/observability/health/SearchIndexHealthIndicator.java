package com.nyasha.store.observability.health;

import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import com.nyasha.store.search.OpenSearchSearchClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchIndexHealthIndicator implements HealthIndicator {

    private final OpenSearchSearchClient openSearchSearchClient;
    private final IndexManagementService indexManagementService;

    public SearchIndexHealthIndicator(
            OpenSearchSearchClient openSearchSearchClient,
            IndexManagementService indexManagementService
    ) {
        this.openSearchSearchClient = openSearchSearchClient;
        this.indexManagementService = indexManagementService;
    }

    @Override
    public Health health() {
        try {
            String openSearchHealth = openSearchSearchClient.clusterHealthStatus();
            boolean openSearchHealthy = openSearchSearchClient.isHealthy();
            IndexingStatusSnapshot indexing = indexManagementService.getStatus();

            Health.Builder builder = Health.up()
                    .withDetails(Map.of(
                            "openSearchClusterHealth", openSearchHealth,
                            "openSearchDocumentCount", indexing.openSearchDocumentCount(),
                            "indexBackpressure", indexing.sustainedBackpressure(),
                            "pendingIndexEvents", indexing.pendingEvents(),
                            "failedIndexEvents", indexing.failedEvents(),
                            "deadLetterIndexEvents", indexing.deadLetterEvents(),
                            "lastSuccessfulIndexTime", indexing.lastSuccessfulIndexTime()
                    ));

            if (!openSearchHealthy) {
                return Health.down()
                        .withException(new IllegalStateException("OpenSearch cluster is unhealthy: " + openSearchHealth))
                        .withDetails(builder.build().getDetails())
                        .build();
            }

            if (indexing.sustainedBackpressure() || indexing.deadLetterEvents() > 0) {
                return Health.down()
                        .withDetails(Map.of(
                                "openSearchClusterHealth", openSearchHealth,
                                "openSearchDocumentCount", indexing.openSearchDocumentCount(),
                                "indexBackpressure", indexing.sustainedBackpressure(),
                                "pendingIndexEvents", indexing.pendingEvents(),
                                "failedIndexEvents", indexing.failedEvents(),
                                "deadLetterIndexEvents", indexing.deadLetterEvents(),
                                "lastSuccessfulIndexTime", indexing.lastSuccessfulIndexTime()
                        ))
                        .withDetail("message", "Indexing pipeline is unhealthy")
                        .build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
