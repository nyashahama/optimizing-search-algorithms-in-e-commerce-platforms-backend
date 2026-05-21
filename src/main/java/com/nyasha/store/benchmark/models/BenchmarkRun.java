package com.nyasha.store.benchmark.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "benchmark_runs")
@Data
public class BenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_set_id", nullable = false)
    private BenchmarkQuerySet querySet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenchmarkRunStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private Integer totalQueries;
    private Integer totalEngines;

    private Long durationMs;
    private Double throughputQueriesPerSecond;
    private Long latencyMinMs;
    private Long latencyP50Ms;
    private Long latencyP95Ms;
    private Long latencyP99Ms;
    private Double latencyAvgMs;
    private Long freshnessP50Ms;
    private Long freshnessP95Ms;
    private Long freshnessP99Ms;
    private Double freshnessAvgMs;
    private String reportDirectory;
}
