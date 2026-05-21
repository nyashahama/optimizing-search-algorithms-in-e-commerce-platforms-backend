package com.nyasha.store.search;

public record SearchQuery(String query, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public SearchQuery {
        query = query == null ? null : query.trim();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("search query is required");
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    public SearchQuery(String query) {
        this(query, DEFAULT_LIMIT);
    }
}
