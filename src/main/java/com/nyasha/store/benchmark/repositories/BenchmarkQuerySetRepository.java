package com.nyasha.store.benchmark.repositories;

import com.nyasha.store.benchmark.models.BenchmarkQuerySet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface BenchmarkQuerySetRepository extends JpaRepository<BenchmarkQuerySet, Long> {
    @EntityGraph(attributePaths = "queries")
    Optional<BenchmarkQuerySet> findByName(String name);

    @EntityGraph(attributePaths = "queries")
    Optional<BenchmarkQuerySet> findById(Long id);
}
