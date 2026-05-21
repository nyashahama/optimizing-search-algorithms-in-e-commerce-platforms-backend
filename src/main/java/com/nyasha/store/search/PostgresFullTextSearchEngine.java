package com.nyasha.store.search;

import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;

import java.util.List;

@Component
public class PostgresFullTextSearchEngine implements ProductSearchEngine {

    private final ProductRepository productRepository;

    public PostgresFullTextSearchEngine(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public SearchEngineType getEngineType() {
        return SearchEngineType.POSTGRES_FTS;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        long startedAt = System.nanoTime();
        try {
            List<Product> products = productRepository.searchByPostgresFullText(query.query(), PageRequest.of(0, query.limit()));
            long elapsedMs = toMs(System.nanoTime() - startedAt);
            return SearchResult.success(getEngineType(), elapsedMs, products);
        } catch (Exception e) {
            long elapsedMs = toMs(System.nanoTime() - startedAt);
            return SearchResult.unsupported(getEngineType(), "PostgreSQL full-text search failed: " + e.getMessage());
        }
    }

    private static long toMs(long elapsedNanos) {
        return elapsedNanos / 1_000_000;
    }
}
