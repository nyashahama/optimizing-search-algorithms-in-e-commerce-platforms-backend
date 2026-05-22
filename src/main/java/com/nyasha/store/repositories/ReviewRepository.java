package com.nyasha.store.repositories;

import com.nyasha.store.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByUserUserIdAndProductProductId(Long userId, Long productId);

    List<Review> findByProductProductId(Long productId);
}
