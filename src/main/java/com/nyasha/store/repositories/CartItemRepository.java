package com.nyasha.store.repositories;

import com.nyasha.store.entities.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartCartIdAndCartItemId(Long cartId, Long cartItemId);

    void deleteByCartCartIdAndCartItemId(Long cartId, Long cartItemId);
}
