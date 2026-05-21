package com.nyasha.store.search;

import com.nyasha.store.entities.Product;

import java.util.List;

public record SearchResult(
        SearchEngineType engine,
        boolean supported,
        long elapsedMs,
        List<Product> products,
        String errorMessage
) {
    public SearchResult {
        if (products == null) {
            products = List.of();
        }
    }

    public int count() {
        return products.size();
    }

    public static SearchResult success(SearchEngineType engine, long elapsedMs, List<Product> products) {
        return new SearchResult(engine, true, elapsedMs, products, null);
    }

    public static SearchResult unsupported(SearchEngineType engine, String errorMessage) {
        return new SearchResult(engine, false, 0L, List.of(), errorMessage);
    }
}
