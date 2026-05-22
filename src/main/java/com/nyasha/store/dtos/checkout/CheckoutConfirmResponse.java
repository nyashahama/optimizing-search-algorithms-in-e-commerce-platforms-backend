package com.nyasha.store.dtos.checkout;

import java.util.List;

public record CheckoutConfirmResponse(
        Long orderId,
        String orderStatus,
        Double subtotal,
        Double discountAmount,
        Double shippingAmount,
        Double taxAmount,
        Double totalAmount,
        String paymentStatus,
        List<CheckoutLineItemResponse> items
) {
}
