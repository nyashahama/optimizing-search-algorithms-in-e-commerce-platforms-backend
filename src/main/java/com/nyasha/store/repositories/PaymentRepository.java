package com.nyasha.store.repositories;

import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrder(Order order);

    Optional<Payment> findByOrderOrderId(Long orderId);
}
