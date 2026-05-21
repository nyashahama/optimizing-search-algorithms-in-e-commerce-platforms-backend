package com.nyasha.store.benchmark.repositories;

import com.nyasha.store.benchmark.models.BenchmarkResult;
import com.nyasha.store.benchmark.models.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkResultRepository extends JpaRepository<BenchmarkResult, Long> {
    List<BenchmarkResult> findByRunOrderByQueryTextAscEngineAsc(BenchmarkRun run);
}
