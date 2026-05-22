package com.nyasha.store.dtos.cart;

public record AddCartItemRequest(Long productId, Long variantId, Integer quantity) {
    public AddCartItemRequest {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }
    }
}
