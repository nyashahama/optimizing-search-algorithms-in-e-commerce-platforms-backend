package com.nyasha.store.controllers;

import com.nyasha.store.dtos.payment.RefundPaymentRequest;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.OrderService;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final OrderService orderService = mock(OrderService.class);
    private final UserService userService = mock(UserService.class);
    private final PaymentController controller = new PaymentController(paymentService, orderService, userService);

    @Test
    void userCanFetchOwnOrderPayment() {
        Payment payment = payment(50.0);
        Order order = new Order();
        order.setOrderId(11L);
        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(1L)));
        when(orderService.getMyOrder(1L, 11L)).thenReturn(order);
        when(paymentService.getPayment(11L)).thenReturn(payment);

        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Payment response = controller.getOrderPayment(auth, 11L);

        assertThat(response).isSameAs(payment);
        verify(orderService).getMyOrder(1L, 11L);
    }

    @Test
    void adminCanFetchAnyOrderPayment() {
        Payment payment = payment(70.0);
        when(userService.getUserByEmail("admin@example.com")).thenReturn(Optional.of(user(99L)));
        when(paymentService.getPayment(11L)).thenReturn(payment);

        Authentication auth = new UsernamePasswordAuthenticationToken("admin@example.com", "secret", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        Payment response = controller.getOrderPayment(auth, 11L);

        assertThat(response).isSameAs(payment);
        verify(orderService, never()).getMyOrder(99L, 11L);
    }

    @Test
    void refundUsesProvidedAmount() {
        Payment payment = payment(125.0);
        when(paymentService.refundPayment(11L, 50.0)).thenReturn(payment);

        assertThat(controller.refundOrderPayment(11L, new RefundPaymentRequest(50.0)).getBody())
                .isSameAs(payment);
    }

    private Payment payment(double amount) {
        Payment payment = new Payment();
        payment.setAmount(amount);
        return payment;
    }

    private User user(Long userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }
}
