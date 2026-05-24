package com.nyasha.store.controllers;

import com.nyasha.store.dtos.order.OrderSummaryDto;
import com.nyasha.store.dtos.order.ShipOrderRequest;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.OrderService;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final UserService userService = mock(UserService.class);
    private final OrderController orderController = new OrderController(orderService, userService);

    @Test
    void orderRoutesDelegateToOrderService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        OrderSummaryDto orderSummary = new OrderSummaryDto(
                11L,
                "PAID",
                LocalDateTime.now(),
                12.34,
                "CAPTURED",
                "PACKED",
                List.of()
        );

        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(3L)));
        when(orderService.getOrderSummaries(3L)).thenReturn(List.of(orderSummary));
        when(orderService.getMyOrderSummary(3L, 11L)).thenReturn(orderSummary);

        Order sampleOrder = new Order();
        when(orderService.packOrder(11L)).thenReturn(sampleOrder);
        when(orderService.shipOrder(11L, "TRACK", "UPS")).thenReturn(sampleOrder);
        when(orderService.deliverOrder(11L)).thenReturn(sampleOrder);
        when(orderService.cancelOrder(3L, 11L)).thenReturn(sampleOrder);

        assertThat(orderController.myOrders(auth)).containsExactly(orderSummary);
        assertThat(orderController.myOrder(auth, 11L)).isSameAs(orderSummary);
        assertThat(orderController.pack(11L).getBody()).isSameAs(sampleOrder);

        ResponseEntity<Order> shipped = orderController.ship(11L, new ShipOrderRequest("TRACK", "UPS"));
        ResponseEntity<Order> delivered = orderController.delivered(11L);
        ResponseEntity<Order> cancelled = orderController.cancel(auth, 11L);

        assertThat(shipped.getBody()).isSameAs(sampleOrder);
        assertThat(delivered.getBody()).isSameAs(sampleOrder);
        assertThat(cancelled.getBody()).isSameAs(sampleOrder);

        verify(orderService).shipOrder(11L, "TRACK", "UPS");
        verify(orderService).cancelOrder(3L, 11L);
    }

    @Test
    void shipAllowsNullPayloadDefaultsTrackingToNull() {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@example.com", "secret");
        when(userService.getUserByEmail("admin@example.com")).thenReturn(Optional.of(user(99L)));
        when(orderService.shipOrder(9L, null, null)).thenReturn(new Order());

        ResponseEntity<Order> response = orderController.ship(9L, null);

        assertThat(response.getBody()).isNotNull();
    }

    private User user(Long id) {
        User u = new User();
        u.setUserId(id);
        return u;
    }
}
