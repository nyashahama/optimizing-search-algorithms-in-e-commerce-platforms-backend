package com.nyasha.store.services;

import com.nyasha.store.dtos.review.CreateReviewRequest;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.Review;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ReviewRepository;
import com.nyasha.store.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public Review createReview(Long userId, CreateReviewRequest request) {
        if (request == null || request.productId() == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        reviewRepository.findByUserUserIdAndProductProductId(userId, request.productId())
                .ifPresent(existing -> {
                    throw new RuntimeException("User already reviewed this product");
                });

        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setDate(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    public List<Review> listForProduct(Long productId) {
        return reviewRepository.findByProductProductId(productId);
    }
}
