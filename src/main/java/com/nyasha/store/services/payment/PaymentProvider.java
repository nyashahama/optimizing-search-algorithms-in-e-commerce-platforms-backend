package com.nyasha.store.services.payment;

public interface PaymentProvider {
    PaymentInitiation initiatePayment(Long orderId, double amount);

    void capturePayment(String transactionId);

    void refundPayment(String transactionId, double amount);
}
