package com.nyasha.store.controllers;

import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexManagementService indexManagementService;

    public IndexController(IndexManagementService indexManagementService) {
        this.indexManagementService = indexManagementService;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Void> rebuild() {
        indexManagementService.rebuildIndex();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    public ResponseEntity<IndexingStatusSnapshot> status() {
        return ResponseEntity.ok(indexManagementService.getStatus());
    }
}
