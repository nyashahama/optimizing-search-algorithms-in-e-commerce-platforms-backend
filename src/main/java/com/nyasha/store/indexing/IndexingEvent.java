package com.nyasha.store.indexing;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "indexing_events")
@Data
public class IndexingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexingEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexingEventStatus status;

    @Column(nullable = false)
    private LocalDateTime eventTime;

    private LocalDateTime processedAt;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(length = 2048)
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String payload;
}
