package com.nyasha.store.controllers;

import com.nyasha.store.search.SearchCompareResponse;
import com.nyasha.store.search.SearchResponse;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.indexing.SearchIndexSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final ProductSearchService productSearchService;
    private final SearchIndexSyncService searchIndexSyncService;

    public SearchController(ProductSearchService productSearchService, SearchIndexSyncService searchIndexSyncService) {
        this.productSearchService = productSearchService;
        this.searchIndexSyncService = searchIndexSyncService;
    }

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "engine", defaultValue = "in_memory") String engine,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit
    ) {
        return ResponseEntity.ok(new SearchResponse(query, productSearchService.search(engine, query, limit)));
    }

    @GetMapping("/compare")
    public ResponseEntity<SearchCompareResponse> compare(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit
    ) {
        return ResponseEntity.ok(productSearchService.compare(query, limit));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "8") Integer limit
    ) {
        try {
            return ResponseEntity.ok(searchIndexSyncService.suggest(query, limit == null ? 8 : limit));
        } catch (RuntimeException e) {
            logger.warn("Autocomplete unavailable from OpenSearch backend, returning empty list: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }
}
