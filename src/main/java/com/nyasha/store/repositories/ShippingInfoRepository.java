package com.nyasha.store.repositories;

import com.nyasha.store.entities.Order;
import com.nyasha.store.entities.ShippingInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShippingInfoRepository extends JpaRepository<ShippingInfo, Long> {
    Optional<ShippingInfo> findByOrder(Order order);
}
