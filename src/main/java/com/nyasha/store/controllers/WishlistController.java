package com.nyasha.store.controllers;

import com.nyasha.store.dtos.wishlist.AddWishlistItemRequest;
import com.nyasha.store.entities.Wishlist;
import com.nyasha.store.services.UserService;
import com.nyasha.store.services.WishlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserService userService;

    public WishlistController(WishlistService wishlistService, UserService userService) {
        this.wishlistService = wishlistService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public Wishlist myWishlist(Authentication authentication) {
        return wishlistService.getOrCreate(currentUserId(authentication));
    }

    @PostMapping("/me/items")
    public ResponseEntity<Wishlist> add(Authentication authentication, @RequestBody AddWishlistItemRequest request) {
        return ResponseEntity.ok(wishlistService.addItem(currentUserId(authentication), request));
    }

    @DeleteMapping("/me/items/{itemId}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable Long itemId) {
        wishlistService.removeItem(currentUserId(authentication), itemId);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
