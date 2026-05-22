package com.nyasha.store.controllers;

import com.nyasha.store.dtos.checkout.CheckoutConfirmRequest;
import com.nyasha.store.dtos.checkout.CheckoutConfirmResponse;
import com.nyasha.store.dtos.checkout.CheckoutPreviewRequest;
import com.nyasha.store.dtos.checkout.CheckoutPreviewResponse;
import com.nyasha.store.services.CheckoutService;
import com.nyasha.store.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkouts")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final UserService userService;

    public CheckoutController(CheckoutService checkoutService, UserService userService) {
        this.checkoutService = checkoutService;
        this.userService = userService;
    }

    @PostMapping("/preview")
    public CheckoutPreviewResponse preview(
            Authentication authentication,
            @RequestBody(required = false) CheckoutPreviewRequest request
    ) {
        return checkoutService.preview(currentUserId(authentication), request);
    }

    @PostMapping("/confirm")
    public ResponseEntity<CheckoutConfirmResponse> confirm(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CheckoutConfirmRequest request
    ) {
        return ResponseEntity.ok(checkoutService.confirm(currentUserId(authentication), request, idempotencyKey));
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
