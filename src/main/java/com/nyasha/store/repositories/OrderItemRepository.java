package com.nyasha.store.repositories;

import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderOrderId(Long orderId);

    Optional<OrderItem> findByOrderAndProductProductId(Order order, Long productId);
}
