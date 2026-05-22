package com.nyasha.store.dtos.order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryDto(
        Long orderId,
        String status,
        LocalDateTime orderDate,
        Double totalAmount,
        String paymentStatus,
        String shippingStatus,
        List<OrderSummaryItemDto> items
) {
}
