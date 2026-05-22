package com.nyasha.store.services.payment;

import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.enums.PaymentStatus;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPaymentUsesProviderTransactionAndPersists() {
        Order order = new Order();
        order.setOrderId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentProvider.initiatePayment(1L, 120.00)).thenReturn(new PaymentInitiation("tx-1", PaymentStatus.PENDING_CAPTURE.name()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.createPayment(1L, "SIMULATED", 120.00);

        assertThat(payment.getTransactionId()).isEqualTo("tx-1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_CAPTURE.name());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void capturePaymentMarksCaptured() {
        Order order = new Order();
        order.setOrderId(1L);
        Payment payment = payment(order, "tx-1", PaymentStatus.PENDING_CAPTURE);
        when(paymentRepository.findByOrderOrderId(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        Payment captured = paymentService.capturePayment(1L);

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED.name());
        verify(paymentProvider).capturePayment("tx-1");
    }

    @Test
    void refundPaymentMarksRefunded() {
        Order order = new Order();
        order.setOrderId(1L);
        Payment payment = payment(order, "tx-1", PaymentStatus.CAPTURED);
        when(paymentRepository.findByOrderOrderId(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        Payment refunded = paymentService.refundPayment(1L, 50.0);

        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
        verify(paymentProvider).refundPayment("tx-1", 50.0);
    }

    @Test
    void capturePaymentMissingPaymentRecordThrows() {
        when(paymentRepository.findByOrderOrderId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.capturePayment(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found");
    }

    private Payment payment(Order order, String txId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setTransactionId(txId);
        payment.setStatus(status.name());
        return payment;
    }
}

