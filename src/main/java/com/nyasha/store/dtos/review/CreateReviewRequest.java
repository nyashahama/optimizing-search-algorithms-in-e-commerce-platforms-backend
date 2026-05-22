package com.nyasha.store.dtos.review;

public record CreateReviewRequest(Long productId, Integer rating, String comment) {
}
