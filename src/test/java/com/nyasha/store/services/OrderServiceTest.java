package com.nyasha.store.services;

import com.nyasha.store.dtos.order.OrderSummaryDto;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.ShippingInfo;
import com.nyasha.store.entities.User;
import com.nyasha.store.enums.OrderStatus;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.PaymentRepository;
import com.nyasha.store.repositories.ShippingInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ShippingInfoRepository shippingInfoRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void packOrderTransitionsPaidToPacked() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order packed = orderService.packOrder(1L);

        assertThat(packed.getStatus()).isEqualTo(OrderStatus.PACKED.name());
    }

    @Test
    void shipOrderRequiresPackedState() {
        Order order = order(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.shipOrder(1L, "tracking", "carrier"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("must be packed");
    }

    @Test
    void shipOrderCreatesShippingInfoAndMarksShipped() {
        Order order = order(OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shippingInfoRepository.findByOrder(order)).thenReturn(Optional.empty());
        when(shippingInfoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order shipped = orderService.shipOrder(1L, "TRK-1", "UPS");

        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED.name());
        verify(shippingInfoRepository).save(any(ShippingInfo.class));
    }

    @Test
    void deliverOrderRequiresShippedState() {
        Order order = order(OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("must be shipped");
    }

    @Test
    void cancelOrderReleasesInventoryAndMarksCancelled() {
        Order order = order(OrderStatus.PAID);
        order.setUser(user(1L));
        OrderItem item = orderItem(order, 10L, 3);
        order.setOrderItems(new HashSet<>(java.util.Set.of(item)));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment(1L, "CAPTURED")));

        Order cancelled = orderService.cancelOrder(1L, 1L);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        verify(inventoryService).release(10L, null, 3);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void getMyOrdersReturnsDtos() {
        when(orderRepository.findByUserUserIdOrderByOrderDateDesc(1L)).thenReturn(java.util.List.of(order(OrderStatus.PAID)));

        java.util.List<OrderSummaryDto> orders = orderService.getOrderSummaries(1L);

        assertThat(orders).hasSize(1);
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setOrderId(1L);
        order.setStatus(status.name());
        order.setTotalAmount(100.0);
        order.setOrderItems(new HashSet<>());
        return order;
    }

    private OrderItem orderItem(Order order, Long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product(productId, 20.0));
        item.setQuantity(quantity);
        item.setPriceAtPurchase(20.0);
        return item;
    }

    private Product product(Long id, Double price) {
        Product product = new Product();
        product.setProductId(id);
        product.setBasePrice(price);
        return product;
    }

    private Payment payment(Long id, String status) {
        Payment payment = new Payment();
        payment.setPaymentId(id);
        payment.setStatus(status);
        return payment;
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
