package com.nyasha.store.benchmark.repositories;

import com.nyasha.store.benchmark.models.BenchmarkQuerySet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BenchmarkQuerySetRepository extends JpaRepository<BenchmarkQuerySet, Long> {
    Optional<BenchmarkQuerySet> findByName(String name);
}
