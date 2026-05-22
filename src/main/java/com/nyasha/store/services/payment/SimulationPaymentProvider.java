package com.nyasha.store.services.payment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimulationPaymentProvider implements PaymentProvider {

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, Double> activePayments = new ConcurrentHashMap<>();

    @Override
    public PaymentInitiation initiatePayment(Long orderId, double amount) {
        String txId = "SIMULATED-" + orderId + "-" + sequence.getAndIncrement();
        activePayments.put(txId, amount);
        return new PaymentInitiation(txId, "PENDING_CAPTURE");
    }

    @Override
    public void capturePayment(String transactionId) {
        if (!activePayments.containsKey(transactionId)) {
            throw new IllegalArgumentException("Unknown transaction: " + transactionId);
        }
    }

    @Override
    public void refundPayment(String transactionId, double amount) {
        Double expected = activePayments.get(transactionId);
        if (expected == null) {
            throw new IllegalArgumentException("Unknown transaction: " + transactionId);
        }
        if (Math.abs(expected - amount) > 0.01) {
            throw new RuntimeException("Refund amount mismatch");
        }
        activePayments.remove(transactionId);
    }
}
