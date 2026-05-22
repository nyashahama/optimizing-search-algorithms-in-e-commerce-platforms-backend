package com.nyasha.store.services;

import com.nyasha.store.dtos.order.OrderSummaryDto;
import com.nyasha.store.dtos.order.OrderSummaryItemDto;
import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Payment;
import com.nyasha.store.entities.ShippingInfo;
import com.nyasha.store.enums.OrderStatus;
import com.nyasha.store.enums.PaymentStatus;
import com.nyasha.store.enums.ShippingStatus;
import com.nyasha.store.repositories.OrderRepository;
import com.nyasha.store.repositories.PaymentRepository;
import com.nyasha.store.repositories.ShippingInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ShippingInfoRepository shippingInfoRepository;
    private final InventoryService inventoryService;

    public OrderService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ShippingInfoRepository shippingInfoRepository,
            InventoryService inventoryService
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.shippingInfoRepository = shippingInfoRepository;
        this.inventoryService = inventoryService;
    }

    public List<OrderSummaryDto> getMyOrders(Long userId) {
        return orderRepository.findByUserUserIdOrderByOrderDateDesc(userId)
                .stream()
                .map(order -> toDto(order))
                .toList();
    }

    public Order getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getUser() == null || !order.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Order not found");
        }

        return order;
    }

    public OrderSummaryDto getMyOrderSummary(Long userId, Long orderId) {
        return toDto(getMyOrder(userId, orderId));
    }

    @Transactional
    public Order packOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus current = parse(order.getStatus());
        if (current == OrderStatus.PACKED) {
            return order;
        }

        if (current != OrderStatus.PAID && current != OrderStatus.CONFIRMED) {
            throw new RuntimeException("Order cannot be packed in current state: " + order.getStatus());
        }

        ShippingInfo shippingInfo = shippingInfoRepository.findByOrder(order)
                .orElseGet(() -> {
                    ShippingInfo created = new ShippingInfo();
                    created.setOrder(order);
                    return created;
                });
        shippingInfo.setStatus(ShippingStatus.PACKED.name());
        shippingInfo.setEstimatedDelivery(LocalDateTime.now().plusDays(5));
        shippingInfoRepository.save(shippingInfo);

        order.setStatus(OrderStatus.PACKED.name());
        return orderRepository.save(order);
    }

    @Transactional
    public Order shipOrder(Long orderId, String trackingNumber, String carrier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus current = parse(order.getStatus());
        if (current == OrderStatus.SHIPPED) {
            return order;
        }
        if (current != OrderStatus.PACKED) {
            throw new RuntimeException("Order must be packed before shipping");
        }

        ShippingInfo shippingInfo = shippingInfoRepository.findByOrder(order)
                .orElseGet(() -> {
                    ShippingInfo shipping = new ShippingInfo();
                    shipping.setOrder(order);
                    return shipping;
                });
        shippingInfo.setTrackingNumber(trackingNumber);
        shippingInfo.setCarrier(carrier);
        shippingInfo.setStatus(ShippingStatus.SHIPPED.name());
        shippingInfo.setEstimatedDelivery(LocalDateTime.now().plusDays(5));
        shippingInfoRepository.save(shippingInfo);

        order.setStatus(OrderStatus.SHIPPED.name());
        return orderRepository.save(order);
    }

    @Transactional
    public Order deliverOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus current = parse(order.getStatus());
        if (current == OrderStatus.DELIVERED) {
            return order;
        }
        if (current != OrderStatus.SHIPPED) {
            throw new RuntimeException("Order must be shipped before delivery");
        }

        shippingInfoRepository.findByOrder(order).ifPresent(shippingInfo -> {
            shippingInfo.setStatus(ShippingStatus.DELIVERED.name());
            shippingInfo.setEstimatedDelivery(LocalDateTime.now());
            shippingInfoRepository.save(shippingInfo);
        });

        order.setStatus(OrderStatus.DELIVERED.name());
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = getMyOrder(userId, orderId);

        OrderStatus current = parse(order.getStatus());
        if (current == OrderStatus.CANCELLED || current == OrderStatus.DELIVERED || current == OrderStatus.RETURNED || current == OrderStatus.COMPLETED) {
            throw new RuntimeException("Order cannot be cancelled in current state: " + order.getStatus());
        }
        if (current != OrderStatus.PENDING_PAYMENT && current != OrderStatus.CONFIRMED && current != OrderStatus.PAID && current != OrderStatus.PACKED) {
            throw new RuntimeException("Order cannot be cancelled in current state: " + order.getStatus());
        }

        for (OrderItem item : order.getOrderItems()) {
            if (item.getProduct() == null || item.getProduct().getProductId() == null) {
                continue;
            }
            inventoryService.release(
                    item.getProduct().getProductId(),
                    item.getVariant() == null ? null : item.getVariant().getVariantId(),
                    item.getQuantity() == null ? 0 : item.getQuantity()
            );
        }

        paymentRepository.findByOrder(order).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED.name());
            paymentRepository.save(payment);
        });

        shippingInfoRepository.findByOrder(order).ifPresent(shippingInfo -> {
            shippingInfo.setStatus(ShippingStatus.CANCELLED.name());
            shippingInfoRepository.save(shippingInfo);
        });

        order.setStatus(OrderStatus.CANCELLED.name());
        return orderRepository.save(order);
    }

    public List<OrderSummaryDto> getOrderSummaries(Long userId) {
        return getMyOrders(userId);
    }

    private OrderSummaryDto toDto(Order order) {
        List<OrderSummaryItemDto> items = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            items.add(new OrderSummaryItemDto(
                    item.getOrderItemId(),
                    item.getProduct() == null ? null : item.getProduct().getProductId(),
                    item.getVariant() == null ? null : item.getVariant().getVariantId(),
                    item.getQuantity(),
                    priceAtPurchase(item),
                    safeLineTotal(item)
            ));
        }

        Payment payment = paymentRepository.findByOrder(order).orElse(null);
        String paymentStatus = payment == null ? null : payment.getStatus();

        ShippingInfo shipping = shippingInfoRepository.findByOrder(order).orElse(null);
        String shippingStatus = shipping == null ? null : shipping.getStatus();

        return new OrderSummaryDto(
                order.getOrderId(),
                order.getStatus(),
                order.getOrderDate(),
                order.getTotalAmount(),
                paymentStatus,
                shippingStatus,
                items
        );
    }

    private OrderStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return OrderStatus.DRAFT;
        }
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unsupported order status: " + value);
        }
    }

    private Double priceAtPurchase(OrderItem item) {
        return item == null ? null : item.getPriceAtPurchase();
    }

    private Double safeLineTotal(OrderItem item) {
        Double unitPrice = priceAtPurchase(item);
        Integer quantity = item == null ? null : item.getQuantity();
        return (unitPrice == null || quantity == null) ? 0.0 : unitPrice * quantity;
    }
}
