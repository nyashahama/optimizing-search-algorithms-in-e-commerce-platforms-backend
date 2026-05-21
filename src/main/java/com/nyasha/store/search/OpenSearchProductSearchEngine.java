package com.nyasha.store.search;

import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenSearchProductSearchEngine implements ProductSearchEngine {

    private final OpenSearchSearchClient openSearchSearchClient;
    private final ProductRepository productRepository;

    public OpenSearchProductSearchEngine(
            OpenSearchSearchClient openSearchSearchClient,
            ProductRepository productRepository
    ) {
        this.openSearchSearchClient = openSearchSearchClient;
        this.productRepository = productRepository;
    }

    @Override
    public SearchEngineType getEngineType() {
        return SearchEngineType.OPENSEARCH;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        long startedAt = System.nanoTime();
        try {
            List<Long> productIds = openSearchSearchClient.searchProductIds(query.query(), query.limit());
            List<Product> orderedProducts = productIds.stream()
                    .map(productRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
            long elapsedMs = toMs(System.nanoTime() - startedAt);
            return SearchResult.success(getEngineType(), elapsedMs, orderedProducts);
        } catch (Exception e) {
            long elapsedMs = toMs(System.nanoTime() - startedAt);
            return SearchResult.unsupported(getEngineType(), "OpenSearch search failed: " + e.getMessage());
        }
    }

    private static long toMs(long elapsedNanos) {
        return elapsedNanos / 1_000_000;
    }
}
