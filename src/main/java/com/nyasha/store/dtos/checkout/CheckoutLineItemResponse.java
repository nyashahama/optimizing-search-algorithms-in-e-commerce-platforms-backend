package com.nyasha.store.dtos.checkout;

public record CheckoutLineItemResponse(
        Long productId,
        Long variantId,
        String productName,
        Integer quantity,
        Double unitPrice,
        Double lineTotal
) {
}
