package com.nyasha.store.dtos.checkout;

public record CheckoutConfirmRequest(
        String paymentMethod,
        String couponCode,
        Double shippingCost,
        Double taxRate,
        Long shippingAddressId
) {
}
