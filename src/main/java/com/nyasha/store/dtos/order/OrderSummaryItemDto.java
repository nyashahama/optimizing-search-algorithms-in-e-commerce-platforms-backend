package com.nyasha.store.dtos.order;

public record OrderSummaryItemDto(
        Long orderItemId,
        Long productId,
        Long variantId,
        Integer quantity,
        Double priceAtPurchase,
        Double lineTotal
) {
}
