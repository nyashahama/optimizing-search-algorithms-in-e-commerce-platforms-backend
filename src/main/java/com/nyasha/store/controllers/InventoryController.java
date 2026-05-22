package com.nyasha.store.controllers;

import com.nyasha.store.dtos.inventory.InventoryAdjustRequest;
import com.nyasha.store.dtos.inventory.InventoryResponse;
import com.nyasha.store.dtos.inventory.InventoryUpsertRequest;
import com.nyasha.store.services.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/low-stock")
    public List<InventoryResponse> getLowStock() {
        return inventoryService.lowStock().stream().map(InventoryResponse::from).toList();
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId
    ) {
        return InventoryResponse.from(inventoryService.getInventory(productId, variantId));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> upsertInventory(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId,
            @RequestBody InventoryUpsertRequest request
    ) {
        if (request == null || request.quantity() == null) {
            throw new IllegalArgumentException("quantity is required");
        }

        return ResponseEntity.ok(InventoryResponse.from(
                inventoryService.upsert(
                        productId,
                        variantId,
                        request.quantity(),
                        request.location(),
                        request.reorderThreshold()
                )
        ));
    }

    @PatchMapping("/{productId}/adjust")
    public ResponseEntity<InventoryResponse> adjustInventory(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId,
            @RequestBody InventoryAdjustRequest request
    ) {
        if (request == null || request.delta() == null) {
            throw new IllegalArgumentException("delta is required");
        }

        return ResponseEntity.ok(InventoryResponse.from(
                inventoryService.adjust(productId, variantId, request.delta())
        ));
    }
}
