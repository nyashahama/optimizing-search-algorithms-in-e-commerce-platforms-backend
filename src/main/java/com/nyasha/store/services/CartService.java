package com.nyasha.store.services;

import com.nyasha.store.dtos.cart.AddCartItemRequest;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.entities.CartItem;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.ProductVariant;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.CartItemRepository;
import com.nyasha.store.repositories.CartRepository;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ProductVariantRepository;
import com.nyasha.store.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Service
public class CartService {
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartItemRepository cartItemRepository;

    public CartService(
            CartRepository cartRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            CartItemRepository cartItemRepository
    ) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public Cart getOrCreateForUser(Long userId) {
        User user = requireUser(userId);
        return cartRepository.findByUserUserId(userId)
                .orElseGet(() -> createCartForUser(user));
    }

    public Cart getForUser(Long userId) {
        return getOrCreateForUser(userId);
    }

    public Cart addItem(Long userId, AddCartItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }
        if (request.productId() == null) {
            throw new IllegalArgumentException("productId is required");
        }

        Cart cart = getOrCreateForUser(userId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductVariant variant = resolveVariant(request.variantId(), product);

        CartItem existing = findMatchingItem(cart, product, variant);
        if (existing != null) {
            existing.setQuantity(safeAdd(existing.getQuantity(), request.quantity()));
            cartItemRepository.save(existing);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(request.quantity());
            cartItemRepository.save(item);
            cart.getCartItems().add(item);
        }

        return getForUser(userId);
    }

    public Cart updateItemQuantity(Long userId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }

        Cart cart = getOrCreateForUser(userId);
        CartItem item = cartItemRepository.findByCartCartIdAndCartItemId(cart.getCartId(), cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return getForUser(userId);
    }

    public void removeItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateForUser(userId);
        CartItem item = cartItemRepository.findByCartCartIdAndCartItemId(cart.getCartId(), cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        cartItemRepository.deleteByCartCartIdAndCartItemId(cart.getCartId(), item.getCartItemId());
    }

    public void clear(Long userId) {
        Cart cart = getOrCreateForUser(userId);
        Set<CartItem> items = cart.getCartItems();
        if (items != null && !items.isEmpty()) {
            cartItemRepository.deleteAll(items);
            items.clear();
            cartRepository.save(cart);
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Cart createCartForUser(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        cart.setCreatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    private ProductVariant resolveVariant(Long variantId, Product product) {
        if (variantId == null) {
            return null;
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        if (!product.getProductId().equals(variant.getProduct().getProductId())) {
            throw new RuntimeException("Variant does not belong to product");
        }

        return variant;
    }

    private CartItem findMatchingItem(Cart cart, Product product, ProductVariant variant) {
        return cart.getCartItems() == null ? null : cart.getCartItems().stream()
                .filter(item -> item.getProduct() != null)
                .filter(item -> product.getProductId().equals(item.getProduct().getProductId()))
                .filter(item -> Objects.equals(variantId(item.getVariant()), variantId(variant)))
                .findFirst()
                .orElse(null);
    }

    private Long variantId(ProductVariant variant) {
        return variant == null ? null : variant.getVariantId();
    }

    private int safeAdd(Integer existing, int add) {
        int base = existing == null ? 0 : existing;
        return Math.addExact(base, add);
    }
}
