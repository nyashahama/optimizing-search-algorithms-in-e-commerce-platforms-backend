package com.nyasha.store.repositories;

import com.nyasha.store.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserUserIdOrderByOrderDateDesc(Long userId);
}
