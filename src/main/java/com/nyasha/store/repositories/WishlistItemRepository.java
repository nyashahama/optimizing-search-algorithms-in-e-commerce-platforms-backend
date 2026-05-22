package com.nyasha.store.repositories;

import com.nyasha.store.entities.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    Optional<WishlistItem> findByWishlistWishlistIdAndProductProductIdAndVariantVariantId(Long wishlistId, Long productId, Long variantId);

    Optional<WishlistItem> findByWishlistWishlistIdAndWishlistItemId(Long wishlistId, Long wishlistItemId);
}
