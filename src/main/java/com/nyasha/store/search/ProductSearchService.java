package com.nyasha.store.search;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductSearchService {

    private final Map<SearchEngineType, ProductSearchEngine> engines;

    public ProductSearchService(List<ProductSearchEngine> searchEngines) {
        this.engines = new EnumMap<>(SearchEngineType.class);
        searchEngines.forEach(engine -> this.engines.put(engine.getEngineType(), engine));
    }

    public SearchResult search(String engine, String query, Integer limit) {
        SearchEngineType engineType = SearchEngineType.from(engine);
        SearchQuery searchQuery = new SearchQuery(query, limit == null ? SearchQuery.DEFAULT_LIMIT : limit);
        ProductSearchEngine searchEngine = engines.get(engineType);

        if (searchEngine == null) {
            return SearchResult.unsupported(engineType, "Search engine not available in this phase.");
        }

        return searchEngine.search(searchQuery);
    }

    public SearchCompareResponse compare(String query, Integer limit) {
        SearchQuery searchQuery = new SearchQuery(query, limit == null ? SearchQuery.DEFAULT_LIMIT : limit);
        List<SearchResult> results = new ArrayList<>();

        for (SearchEngineType engineType : SearchEngineType.values()) {
            ProductSearchEngine searchEngine = engines.get(engineType);
            if (searchEngine == null) {
                results.add(SearchResult.unsupported(engineType, "Search engine not available in this phase."));
            } else {
                results.add(searchEngine.search(searchQuery));
            }
        }
        return new SearchCompareResponse(searchQuery.query(), searchQuery.limit(), results);
    }
}
