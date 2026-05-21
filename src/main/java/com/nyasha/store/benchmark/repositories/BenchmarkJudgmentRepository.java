package com.nyasha.store.benchmark.repositories;

import com.nyasha.store.benchmark.models.BenchmarkJudgment;
import com.nyasha.store.benchmark.models.BenchmarkQuerySet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkJudgmentRepository extends JpaRepository<BenchmarkJudgment, Long> {
    List<BenchmarkJudgment> findByQuerySetAndQueryTextIgnoreCase(BenchmarkQuerySet querySet, String queryText);
}

