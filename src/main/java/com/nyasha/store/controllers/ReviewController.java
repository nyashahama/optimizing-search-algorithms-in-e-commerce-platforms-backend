package com.nyasha.store.controllers;

import com.nyasha.store.dtos.review.CreateReviewRequest;
import com.nyasha.store.entities.Review;
import com.nyasha.store.services.ReviewService;
import com.nyasha.store.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;
    private final UserService userService;

    public ReviewController(ReviewService reviewService, UserService userService) {
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Review> createReview(
            Authentication authentication,
            @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity.ok(reviewService.createReview(currentUserId(authentication), request));
    }

    @GetMapping("/products/{productId}")
    public List<Review> listForProduct(@PathVariable Long productId) {
        return reviewService.listForProduct(productId);
    }

    private Long currentUserId(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        return userService.getUserByEmail(username)
                .map(user -> user.getUserId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
