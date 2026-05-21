package com.nyasha.store.controllers;

import com.nyasha.store.search.SearchCompareResponse;
import com.nyasha.store.search.SearchResponse;
import com.nyasha.store.search.ProductSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final ProductSearchService productSearchService;

    public SearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
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
}
