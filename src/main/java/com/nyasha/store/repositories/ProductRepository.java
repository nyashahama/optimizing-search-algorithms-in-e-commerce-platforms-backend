package com.nyasha.store.repositories;

import com.nyasha.store.entities.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ProductRepository extends JpaRepository<Product,Long> {
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description, Pageable pageable);
    List<Product> findBySku(String sku);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.categoryId = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);
}
