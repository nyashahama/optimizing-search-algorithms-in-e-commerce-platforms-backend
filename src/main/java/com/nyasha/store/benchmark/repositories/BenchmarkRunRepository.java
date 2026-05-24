package com.nyasha.store.benchmark.repositories;

import com.nyasha.store.benchmark.models.BenchmarkRun;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

import java.util.Optional;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    @EntityGraph(attributePaths = "querySet")
    Optional<BenchmarkRun> findById(Long id);

    List<BenchmarkRun> findByStatusOrderByStartedAtDesc(BenchmarkRunStatus status);

    long countByStatus(BenchmarkRunStatus status);
}
