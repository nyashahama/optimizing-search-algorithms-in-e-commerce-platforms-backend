package com.nyasha.store.search;

import org.springframework.stereotype.Component;

@Component
public class OpenSearchProductSearchEngine implements ProductSearchEngine {

    @Override
    public SearchEngineType getEngineType() {
        return SearchEngineType.OPENSEARCH;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        return SearchResult.unsupported(
                getEngineType(),
                "OpenSearch search is planned for a later phase."
        );
    }
}
