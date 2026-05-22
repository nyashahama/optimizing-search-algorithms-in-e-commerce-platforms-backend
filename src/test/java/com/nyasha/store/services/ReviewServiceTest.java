package com.nyasha.store.services;

import com.nyasha.store.dtos.review.CreateReviewRequest;
import com.nyasha.store.entities.Product;
import com.nyasha.store.entities.Review;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.repositories.ReviewRepository;
import com.nyasha.store.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void createReviewReturnsPersistedReview() {
        Product product = product(10L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(reviewRepository.findByUserUserIdAndProductProductId(1L, 10L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Review review = reviewService.createReview(1L, new CreateReviewRequest(10L, 5, "Great"));

        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getComment()).isEqualTo("Great");
    }

    @Test
    void createReviewRejectsOutOfRangeRating() {
        assertThatThrownBy(() -> reviewService.createReview(1L, new CreateReviewRequest(10L, 0, "Bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    @Test
    void createReviewRejectsDuplicatePerUserProduct() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product(10L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(reviewRepository.findByUserUserIdAndProductProductId(1L, 10L)).thenReturn(Optional.of(new Review()));

        assertThatThrownBy(() -> reviewService.createReview(1L, new CreateReviewRequest(10L, 4, "again")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void listForProductDelegatesToRepository() {
        Review first = new Review();
        Review second = new Review();
        when(reviewRepository.findByProductProductId(10L)).thenReturn(List.of(first, second));

        List<Review> reviews = reviewService.listForProduct(10L);

        assertThat(reviews).containsExactly(first, second);
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setProductId(id);
        return product;
    }

    private com.nyasha.store.entities.User user(Long id) {
        com.nyasha.store.entities.User user = new com.nyasha.store.entities.User();
        user.setUserId(id);
        return user;
    }
}
