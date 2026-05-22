package com.nyasha.store.services;

import com.nyasha.store.dtos.wishlist.AddWishlistItemRequest;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.ProductVariant;
import com.nyasha.store.entities.User;
import com.nyasha.store.entities.Wishlist;
import com.nyasha.store.entities.WishlistItem;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ProductVariantRepository;
import com.nyasha.store.repositories.UserRepository;
import com.nyasha.store.repositories.WishlistItemRepository;
import com.nyasha.store.repositories.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
public class WishlistService {
    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public WishlistService(
            WishlistRepository wishlistRepository,
            WishlistItemRepository wishlistItemRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
    }

    public Wishlist getOrCreate(Long userId) {
        return wishlistRepository.findByUserUserId(userId)
                .orElseGet(() -> createWishlistForUser(userId));
    }

    public Wishlist addItem(Long userId, AddWishlistItemRequest request) {
        if (request == null || request.productId() == null) {
            throw new IllegalArgumentException("productId is required");
        }

        Wishlist wishlist = getOrCreate(userId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductVariant variant = null;
        if (request.variantId() != null) {
            variant = productVariantRepository.findById(request.variantId())
                    .orElseThrow(() -> new RuntimeException("Product variant not found"));
            if (!product.getProductId().equals(variant.getProduct().getProductId())) {
                throw new RuntimeException("Variant does not belong to product");
            }
        }

        wishlistItemRepository.findByWishlistWishlistIdAndProductProductIdAndVariantVariantId(
                        wishlist.getWishlistId(),
                        request.productId(),
                        request.variantId()
                )
                .ifPresent(existing -> {
                    throw new RuntimeException("Product already in wishlist");
                });

        WishlistItem item = new WishlistItem();
        item.setWishlist(wishlist);
        item.setProduct(product);
        item.setVariant(variant);
        wishlistItemRepository.save(item);

        if (wishlist.getWishlistItems() == null) {
            wishlist.setWishlistItems(new HashSet<>());
        }
        wishlist.getWishlistItems().add(item);
        return wishlistRepository.save(wishlist);
    }

    public void removeItem(Long userId, Long wishlistItemId) {
        Wishlist wishlist = getOrCreate(userId);
        WishlistItem item = wishlistItemRepository.findByWishlistWishlistIdAndWishlistItemId(wishlist.getWishlistId(), wishlistItemId)
                .orElseThrow(() -> new RuntimeException("Wishlist item not found"));

        wishlistItemRepository.delete(item);
    }

    public void clear(Long userId) {
        Wishlist wishlist = getOrCreate(userId);
        wishlistItemRepository.deleteAll(wishlist.getWishlistItems());
        wishlist.getWishlistItems().clear();
        wishlistRepository.save(wishlist);
    }

    private Wishlist createWishlistForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        wishlist.setCreatedAt(java.time.LocalDateTime.now());
        return wishlistRepository.save(wishlist);
    }
}
