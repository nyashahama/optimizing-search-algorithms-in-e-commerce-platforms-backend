package com.nyasha.store.repositories;

import com.nyasha.store.entities.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByUserUserId(Long userId);
}
