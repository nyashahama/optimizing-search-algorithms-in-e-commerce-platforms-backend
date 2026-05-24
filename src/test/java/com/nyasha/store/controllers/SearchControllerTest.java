package com.nyasha.store.controllers;

import com.nyasha.store.search.SearchCompareResponse;
import com.nyasha.store.search.SearchResponse;
import com.nyasha.store.search.SearchResult;
import com.nyasha.store.search.SearchEngineType;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.indexing.SearchIndexSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchControllerTest {

    private final ProductSearchService productSearchService = mock(ProductSearchService.class);
    private final SearchIndexSyncService searchIndexSyncService = mock(SearchIndexSyncService.class);
    private final SearchController searchController = new SearchController(productSearchService, searchIndexSyncService);

    @Test
    void searchCompareAutocompleteDelegateToServices() {
        SearchResult searchResult = SearchResult.success(SearchEngineType.IN_MEMORY, 10L, List.of());
        SearchCompareResponse compareResponse = new SearchCompareResponse("wireless", 20, List.of(searchResult));
        when(productSearchService.search("in_memory", "wireless", 20)).thenReturn(searchResult);
        when(productSearchService.compare("wireless", 20)).thenReturn(compareResponse);
        when(searchIndexSyncService.suggest("wire", 5)).thenReturn(List.of("wireless", "wiring"));

        SearchResponse search = searchController.search("wireless", "in_memory", 20).getBody();
        SearchCompareResponse compare = searchController.compare("wireless", 20).getBody();
        List<String> suggestions = searchController.autocomplete("wire", 5).getBody();

        assertThat(search).isNotNull();
        assertThat(search.query()).isEqualTo("wireless");
        assertThat(compare).isSameAs(compareResponse);
        assertThat(suggestions).containsExactly("wireless", "wiring");
    }
}
