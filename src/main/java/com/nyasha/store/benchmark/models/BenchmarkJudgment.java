package com.nyasha.store.benchmark.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "benchmark_judgments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_judgment", columnNames = {"query_set_id", "query_text", "product_id"})
        }
)
@Data
public class BenchmarkJudgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_set_id", nullable = false)
    private BenchmarkQuerySet querySet;

    @Column(nullable = false)
    private String queryText;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer relevance;
}
