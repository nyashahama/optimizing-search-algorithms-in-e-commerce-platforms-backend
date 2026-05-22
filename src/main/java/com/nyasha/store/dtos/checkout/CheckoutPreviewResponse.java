package com.nyasha.store.dtos.checkout;

import java.util.List;

public record CheckoutPreviewResponse(
        Double subtotal,
        Double discountAmount,
        Double shippingAmount,
        Double taxAmount,
        Double totalAmount,
        List<CheckoutLineItemResponse> items
) {
}
