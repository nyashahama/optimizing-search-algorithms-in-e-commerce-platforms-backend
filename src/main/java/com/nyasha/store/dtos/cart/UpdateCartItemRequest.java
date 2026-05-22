package com.nyasha.store.dtos.cart;

public record UpdateCartItemRequest(Integer quantity) {
    public UpdateCartItemRequest {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }
    }
}
