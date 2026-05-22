package com.nyasha.store.dtos.checkout;

public record CheckoutPreviewRequest(
        String couponCode,
        Double shippingCost,
        Double taxRate,
        Long shippingAddressId
) {
}
