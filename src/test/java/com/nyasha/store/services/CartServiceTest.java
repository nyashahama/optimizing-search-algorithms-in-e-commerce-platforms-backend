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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void getOrCreateForUserReturnsExistingCart() {
        Cart existing = cart(42L, 1L);
        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        Cart cart = cartService.getOrCreateForUser(1L);

        assertThat(cart).isEqualTo(existing);
    }

    @Test
    void getOrCreateForUserCreatesCartWhenMissing() {
        User user = user(1L);
        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.save(any())).thenAnswer(invocation -> {
            Cart saved = invocation.getArgument(0);
            saved.setCartId(10L);
            return saved;
        });

        Cart cart = cartService.getOrCreateForUser(1L);

        assertThat(cart.getCartId()).isEqualTo(10L);
        assertThat(cart.getUser()).isEqualTo(user);
    }

    @Test
    void addItemAppendsNewLineItem() {
        Cart cart = cart(10L, 1L);
        Product product = product(1L, "Laptop", 100.0);

        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(cart));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.addItem(1L, new AddCartItemRequest(1L, null, 2));

        assertThat(result.getCartItems()).hasSize(1);
        CartItem added = result.getCartItems().iterator().next();
        assertThat(added.getProduct()).isEqualTo(product);
        assertThat(added.getQuantity()).isEqualTo(2);
    }

    @Test
    void addItemAddsToExistingCartItemQuantity() {
        Product product = product(1L, "Laptop", 100.0);
        Cart cart = cart(10L, 1L);
        cart.getCartItems().add(cartItem(product, null, 2));
        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(cart));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.addItem(1L, new AddCartItemRequest(1L, null, 3));

        CartItem item = result.getCartItems().iterator().next();
        assertThat(item.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).save(item);
    }

    @Test
    void updateItemQuantityChangesExistingItem() {
        Cart cart = cart(10L, 1L);
        CartItem existing = cartItem(product(1L, "Laptop", 100.0), null, 2);
        cart.getCartItems().add(existing);
        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(cart));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(cartItemRepository.findByCartCartIdAndCartItemId(10L, 9L)).thenReturn(Optional.of(existing));

        Cart result = cartService.updateItemQuantity(1L, 9L, 8);

        assertThat(result.getCartItems().iterator().next().getQuantity()).isEqualTo(8);
    }

    @Test
    void removeItemDeletesMatchingLineItem() {
        User user = user(1L);
        Cart cart = cart(10L, 1L);
        CartItem existing = cartItem(product(1L, "Laptop", 100.0), null, 2);
        existing.setCartItemId(9L);
        cart.getCartItems().add(existing);

        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(cart));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartItemRepository.findByCartCartIdAndCartItemId(10L, 9L)).thenReturn(Optional.of(existing));

        cartService.removeItem(1L, 9L);

        verify(cartItemRepository).deleteByCartCartIdAndCartItemId(10L, 9L);
    }

    @Test
    void clearEmptiesCartItems() {
        User user = user(1L);
        Cart cart = cart(10L, 1L);
        cart.getCartItems().add(cartItem(product(1L, "Laptop", 100.0), null, 2));

        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(cart));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        cartService.clear(1L);

        verify(cartItemRepository).deleteAll(cart.getCartItems());
    }

    @Test
    void addItemRejectsUnknownProduct() {
        Cart cart = cart(10L, 1L);
        when(cartRepository.findByUserUserId(1L)).thenReturn(Optional.of(cart));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(99L, null, 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void updateItemQuantityRequiresPositiveQuantity() {
        assertThatThrownBy(() -> cartService.updateItemQuantity(1L, 9L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be a positive integer");
    }

    private Cart cart(Long cartId, Long userId) {
        Cart cart = new Cart();
        cart.setCartId(cartId);
        cart.setUser(user(userId));
        cart.setCartItems(new java.util.HashSet<>());
        return cart;
    }

    private User user(Long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }

    private Product product(Long id, String name, Double price) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setBasePrice(price);
        return product;
    }

    private CartItem cartItem(Product product, ProductVariant variant, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setVariant(variant);
        item.setQuantity(quantity);
        return item;
    }
}
