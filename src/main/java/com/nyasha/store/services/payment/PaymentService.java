package com.nyasha.store.services.payment;

import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.enums.PaymentStatus;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentProvider paymentProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public Payment createPayment(Long orderId, String method, double amount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        return paymentRepository.findByOrderOrderId(orderId)
                .filter(existing -> !PaymentStatus.FAILED.name().equals(existing.getStatus()))
                .map(existing -> {
                    if (existing.getStatus() == null) {
                        return existing;
                    }

                    String existingMethod = existing.getMethod() == null ? "SIMULATED" : existing.getMethod();
                    String requestedMethod = method == null || method.isBlank() ? "SIMULATED" : method;

                    if (existingMethod.equals(requestedMethod)
                            && Math.abs((existing.getAmount() == null ? 0.0 : existing.getAmount()) - amount) <= 0.01) {
                        return existing;
                    }

                    throw new RuntimeException("Payment already exists for order");
                })
                .orElseGet(() -> {
                    PaymentInitiation initiation = paymentProvider.initiatePayment(orderId, amount);

                    Payment payment = new Payment();
                    payment.setOrder(order);
                    payment.setMethod(method == null || method.isBlank() ? "SIMULATED" : method);
                    payment.setTransactionId(initiation.transactionId());
                    payment.setAmount(amount);
                    payment.setStatus(PaymentStatus.PENDING_CAPTURE.name());
                    payment.setTimestamp(LocalDateTime.now());
                    return paymentRepository.save(payment);
                });
    }

    @Transactional
    public Payment capturePayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
            throw new RuntimeException("Payment provider unavailable");
        }

        if (PaymentStatus.CAPTURED.name().equals(payment.getStatus())) {
            return payment;
        }

        if (PaymentStatus.REFUNDED.name().equals(payment.getStatus())) {
            throw new RuntimeException("Payment already refunded");
        }

        paymentProvider.capturePayment(payment.getTransactionId());
        payment.setStatus(PaymentStatus.CAPTURED.name());
        return paymentRepository.save(payment);
    }

    public Payment getPayment(Long orderId) {
        return paymentRepository.findByOrderOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    @Transactional
    public Payment refundPayment(Long orderId, double amount) {
        Payment payment = paymentRepository.findByOrderOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (PaymentStatus.REFUNDED.name().equals(payment.getStatus())) {
            return payment;
        }

        if (!PaymentStatus.CAPTURED.name().equals(payment.getStatus()) && !PaymentStatus.PENDING_CAPTURE.name().equals(payment.getStatus())) {
            throw new RuntimeException("Payment must be captured before refund");
        }

        if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
            throw new RuntimeException("Payment provider unavailable");
        }

        paymentProvider.refundPayment(payment.getTransactionId(), amount);
        payment.setStatus(PaymentStatus.REFUNDED.name());
        return paymentRepository.save(payment);
    }

}
