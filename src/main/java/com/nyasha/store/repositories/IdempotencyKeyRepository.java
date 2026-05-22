package com.nyasha.store.repositories;

import com.nyasha.store.entities.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByKeyValueAndOperation(String keyValue, String operation);
}
