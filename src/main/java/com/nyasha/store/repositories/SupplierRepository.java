package com.nyasha.store.repositories;

import com.nyasha.store.entities.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByNameIgnoreCase(String name);
}
