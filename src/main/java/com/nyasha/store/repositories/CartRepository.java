package com.nyasha.store.repositories;

import com.nyasha.store.entities.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserUserId(Long userId);
}
