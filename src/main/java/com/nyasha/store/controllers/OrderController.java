package com.nyasha.store.controllers;

import com.nyasha.store.dtos.order.OrderSummaryDto;
import com.nyasha.store.dtos.order.ShipOrderRequest;
import com.nyasha.store.entities.Order;
import com.nyasha.store.services.OrderService;
import com.nyasha.store.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public List<OrderSummaryDto> myOrders(Authentication authentication) {
        return orderService.getOrderSummaries(currentUserId(authentication));
    }

    @GetMapping("/{orderId}")
    public OrderSummaryDto myOrder(Authentication authentication, @PathVariable Long orderId) {
        return orderService.getMyOrderSummary(currentUserId(authentication), orderId);
    }

    @PostMapping("/{orderId}/pack")
    public ResponseEntity<Order> pack(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.packOrder(orderId));
    }

    @PostMapping("/{orderId}/ship")
    public ResponseEntity<Order> ship(
            @PathVariable Long orderId,
            @RequestBody(required = false) ShipOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.shipOrder(
                orderId,
                request == null ? null : request.trackingNumber(),
                request == null ? null : request.carrier()
        ));
    }

    @PostMapping("/{orderId}/delivered")
    public ResponseEntity<Order> delivered(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.deliverOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancel(Authentication authentication, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(currentUserId(authentication), orderId));
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
