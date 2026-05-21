package com.nyasha.store.benchmark.repositories;

import com.nyasha.store.benchmark.models.BenchmarkRun;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    List<BenchmarkRun> findByStatusOrderByStartedAtDesc(BenchmarkRunStatus status);
}

