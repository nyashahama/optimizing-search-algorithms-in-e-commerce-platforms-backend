package com.nyasha.store.indexing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IndexingEventRepository extends JpaRepository<IndexingEvent, Long> {
    Optional<IndexingEvent> findByEventId(String eventId);

    long countByStatus(IndexingEventStatus status);

    List<IndexingEvent> findTop200ByStatusInOrderByEventTimeAsc(List<IndexingEventStatus> statuses);

    Optional<IndexingEvent> findTopByStatusInOrderByEventTimeAsc(List<IndexingEventStatus> statuses);

    Optional<IndexingEvent> findTopByStatusOrderByProcessedAtDesc(IndexingEventStatus status);
}
