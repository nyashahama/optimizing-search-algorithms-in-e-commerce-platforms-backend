package com.nyasha.store.repositories;

import com.nyasha.store.entities.OrderItem;
import com.nyasha.store.entities.Return;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReturnRepository extends JpaRepository<Return, Long> {
    Optional<Return> findByOrderItem(OrderItem orderItem);

    List<Return> findByOrderItemOrderUserUserId(Long userId);
}
