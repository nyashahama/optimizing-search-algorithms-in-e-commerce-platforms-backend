package com.nyasha.store.benchmark.models;

import com.nyasha.store.search.SearchEngineType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "benchmark_results")
@Data
public class BenchmarkResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun run;

    @Column(nullable = false)
    private String queryText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SearchEngineType engine;

    private Long latencyMs;
    private Integer resultCount;
    private Integer returnedCount;

    @Column(length = 2048)
    private String topResultProductIds;

    private Double precisionAtK;
    private Double recallAtK;
    private Double mrrAtK;
    private Double ndcgAtK;

    private String errorMessage;

    public boolean isSupported() {
        return errorMessage == null;
    }
}
