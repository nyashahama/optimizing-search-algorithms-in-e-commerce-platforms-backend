package com.nyasha.store.controllers;

import com.nyasha.store.dtos.payment.RefundPaymentRequest;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.services.OrderService;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.payment.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final UserService userService;

    public PaymentController(PaymentService paymentService, OrderService orderService, UserService userService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.userService = userService;
    }

    @GetMapping("/orders/{orderId}")
    public Payment getOrderPayment(Authentication authentication, @PathVariable Long orderId) {
        Long currentUserId = currentUserId(authentication);
        if (!isAdmin(authentication)) {
            orderService.getMyOrder(currentUserId, orderId);
        }
        return paymentService.getPayment(orderId);
    }

    @PostMapping("/orders/{orderId}/capture")
    public ResponseEntity<Payment> captureOrderPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.capturePayment(orderId));
    }

    @PostMapping("/orders/{orderId}/refund")
    public ResponseEntity<Payment> refundOrderPayment(
            @PathVariable Long orderId,
            @RequestBody(required = false) RefundPaymentRequest request
    ) {
        Payment payment = paymentService.getPayment(orderId);
        double amount = request != null && request.amount() != null
                ? request.amount()
                : payment.getAmount() == null ? 0.0 : payment.getAmount();

        if (amount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        return ResponseEntity.ok(paymentService.refundPayment(orderId, amount));
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority != null && "ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
