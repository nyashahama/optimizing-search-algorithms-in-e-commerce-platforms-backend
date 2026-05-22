package com.nyasha.store.controllers;

import com.nyasha.store.dtos.cart.AddCartItemRequest;
import com.nyasha.store.dtos.cart.UpdateCartItemRequest;
import com.nyasha.store.entities.Cart;
import com.nyasha.store.services.CartService;
import com.nyasha.store.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final CartService cartService;
    private final UserService userService;

    public CartController(CartService cartService, UserService userService) {
        this.cartService = cartService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public Cart getCurrentCart(Authentication authentication) {
        return cartService.getForUser(currentUserId(authentication));
    }

    @PostMapping("/me/items")
    public ResponseEntity<Cart> addItem(
            Authentication authentication,
            @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.addItem(currentUserId(authentication), request));
    }

    @PatchMapping("/me/items/{itemId}")
    public ResponseEntity<Cart> updateItem(
            Authentication authentication,
            @PathVariable Long itemId,
            @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.updateItemQuantity(currentUserId(authentication), itemId, request.quantity()));
    }

    @DeleteMapping("/me/items/{itemId}")
    public ResponseEntity<Void> removeItem(Authentication authentication, @PathVariable Long itemId) {
        cartService.removeItem(currentUserId(authentication), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> clear(Authentication authentication) {
        cartService.clear(currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
