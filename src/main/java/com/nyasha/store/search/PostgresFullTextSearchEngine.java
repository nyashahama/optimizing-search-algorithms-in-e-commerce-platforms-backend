package com.nyasha.store.search;

import org.springframework.stereotype.Component;

@Component
public class PostgresFullTextSearchEngine implements ProductSearchEngine {

    @Override
    public SearchEngineType getEngineType() {
        return SearchEngineType.POSTGRES_FTS;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        return SearchResult.unsupported(
                getEngineType(),
                "PostgreSQL full-text search is planned for a later phase."
        );
    }
}
