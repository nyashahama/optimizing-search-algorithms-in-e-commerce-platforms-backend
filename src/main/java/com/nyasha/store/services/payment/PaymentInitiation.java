package com.nyasha.store.services.payment;

public record PaymentInitiation(
        String transactionId,
        String status
) {
}
