package com.nyasha.store.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"key_value", "operation"})
)
@Data
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_value", nullable = false)
    private String keyValue;

    @Column(nullable = false)
    private String operation;

    private Long userId;

    private Long orderId;

    @Column(columnDefinition = "TEXT")
    private String requestHash;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}
