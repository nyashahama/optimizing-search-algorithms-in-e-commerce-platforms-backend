package com.nyasha.store.benchmark.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "benchmark_queries")
@Data
public class BenchmarkQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_set_id")
    private BenchmarkQuerySet querySet;

    @Column(nullable = false)
    private String queryText;

    @Column(nullable = false)
    private Integer position;
}
