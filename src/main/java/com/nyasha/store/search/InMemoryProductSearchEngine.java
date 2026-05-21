package com.nyasha.store.search;

import com.nyasha.store.entities.Product;
import com.nyasha.store.utils.ProductIndex;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InMemoryProductSearchEngine implements ProductSearchEngine {

    private final ProductIndex productIndex;

    public InMemoryProductSearchEngine(ProductIndex productIndex) {
        this.productIndex = productIndex;
    }

    @Override
    public SearchEngineType getEngineType() {
        return SearchEngineType.IN_MEMORY;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        long startedAt = System.nanoTime();
        try {
            List<Product> products = new ArrayList<>(productIndex.searchByText(query.query()));
            List<Product> limited = products.stream().limit(query.limit()).toList();
            long elapsedMs = toMs(System.nanoTime() - startedAt);
            return SearchResult.success(getEngineType(), elapsedMs, limited);
        } catch (Exception e) {
            long elapsedMs = toMs(System.nanoTime() - startedAt);
            return SearchResult.unsupported(getEngineType(), "Search failed: " + e.getMessage());
        }
    }

    private static long toMs(long elapsedNanos) {
        return elapsedNanos / 1_000_000;
    }
}
