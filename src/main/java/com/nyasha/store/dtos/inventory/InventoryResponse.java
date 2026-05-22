package com.nyasha.store.dtos.inventory;

import com.nyasha.store.entities.Inventory;

public record InventoryResponse(
        Long inventoryId,
        Long productId,
        Long variantId,
        Integer quantity,
        String location,
        Integer reorderThreshold,
        boolean lowStock
) {
    public static InventoryResponse from(Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        Integer quantity = inventory.getQuantity();
        Integer reorderThreshold = inventory.getReorderThreshold();
        boolean lowStock = reorderThreshold != null && (quantity == null ? 0 : quantity) <= reorderThreshold;

        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getProduct() == null ? null : inventory.getProduct().getProductId(),
                inventory.getVariant() == null ? null : inventory.getVariant().getVariantId(),
                quantity,
                inventory.getLocation(),
                reorderThreshold,
                lowStock
        );
    }
}
