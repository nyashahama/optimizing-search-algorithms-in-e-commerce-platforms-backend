package com.nyasha.store.controllers;

import com.nyasha.store.dtos.review.CreateReviewRequest;
import com.nyasha.store.entities.Review;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.ReviewService;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewControllerTest {

    private final ReviewService reviewService = mock(ReviewService.class);
    private final UserService userService = mock(UserService.class);
    private final ReviewController reviewController = new ReviewController(reviewService, userService);

    @Test
    void reviewRoutesDelegateToReviewService() {
        Authentication auth = new UsernamePasswordAuthenticationToken("buyer@example.com", "secret");
        CreateReviewRequest request = new CreateReviewRequest(1L, 5, "Great service and fast shipping");
        Review review = new Review();

        when(userService.getUserByEmail("buyer@example.com")).thenReturn(Optional.of(user(7L)));
        when(reviewService.createReview(7L, request)).thenReturn(review);
        when(reviewService.listForProduct(1L)).thenReturn(List.of(review));

        ResponseEntity<Review> created = ResponseEntity.ok(review);

        assertThat(reviewController.createReview(auth, request).getBody()).isSameAs(review);
        assertThat(reviewController.listForProduct(1L)).containsExactly(review);

        verify(userService).getUserByEmail("buyer@example.com");
        verify(reviewService).createReview(7L, request);
        verify(reviewService).listForProduct(1L);

        assertThat(created.getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void createReviewRequiresResolvableUser() {
        Authentication auth = new UsernamePasswordAuthenticationToken("missing@example.com", "secret");
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewController.createReview(auth, new CreateReviewRequest(1L, 4, "Could be better")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authenticated user not found");
    }

    private User user(long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }
}
