package com.nyasha.store.search;

public interface ProductSearchEngine {
    SearchEngineType getEngineType();

    SearchResult search(SearchQuery query);
}
