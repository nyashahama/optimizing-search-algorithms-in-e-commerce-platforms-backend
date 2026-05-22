package com.nyasha.store.dtos.inventory;

public record InventoryUpsertRequest(
        Integer quantity,
        String location,
        Integer reorderThreshold
) {
}
