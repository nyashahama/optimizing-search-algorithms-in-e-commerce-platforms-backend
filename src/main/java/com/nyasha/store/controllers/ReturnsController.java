package com.nyasha.store.controllers;

import com.nyasha.store.dtos.returns.CreateReturnRequest;
import com.nyasha.store.entities.Return;
import java.util.List;
import com.nyasha.store.services.ReturnService;
import com.nyasha.store.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/returns")
public class ReturnsController {

    private final ReturnService returnService;
    private final UserService userService;

    public ReturnsController(ReturnService returnService, UserService userService) {
        this.returnService = returnService;
        this.userService = userService;
    }

    @PostMapping("/{orderId}")
    public ResponseEntity<Return> openReturn(
            Authentication authentication,
            @PathVariable Long orderId,
            @RequestBody CreateReturnRequest request
    ) {
        return ResponseEntity.ok(returnService.openReturn(currentUserId(authentication), orderId, request));
    }

    @GetMapping("/me")
    public List<Return> myReturns(Authentication authentication) {
        return returnService.getReturnsForUser(currentUserId(authentication));
    }

    @PostMapping("/{returnId}/approve")
    public ResponseEntity<Return> approve(@PathVariable Long returnId) {
        return ResponseEntity.ok(returnService.approveReturn(returnId));
    }

    @PostMapping("/{returnId}/reject")
    public ResponseEntity<Return> reject(@PathVariable Long returnId) {
        return ResponseEntity.ok(returnService.rejectReturn(returnId));
    }

    @PostMapping("/{returnId}/refund")
    public ResponseEntity<Return> refund(
            Authentication authentication,
            @PathVariable Long returnId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        return ResponseEntity.ok(returnService.refundReturn(returnId, currentUserId(authentication), idempotencyKey));
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
