package com.nyasha.store.repositories;

import com.nyasha.store.entities.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.product.productId = :productId
            AND (
                (:variantId IS NULL AND i.variant IS NULL)
                OR i.variant.variantId = :variantId
            )
            """)
    Optional<Inventory> findForUpdate(@Param("productId") Long productId, @Param("variantId") Long variantId);

    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.product.productId = :productId
            AND (
                (:variantId IS NULL AND i.variant IS NULL)
                OR i.variant.variantId = :variantId
            )
            """)
    Optional<Inventory> findByProductAndVariant(@Param("productId") Long productId, @Param("variantId") Long variantId);

    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.reorderThreshold IS NOT NULL
            AND COALESCE(i.quantity, 0) <= i.reorderThreshold
            ORDER BY COALESCE(i.quantity, 0) ASC
            """)
    List<Inventory> findLowStock();
}
