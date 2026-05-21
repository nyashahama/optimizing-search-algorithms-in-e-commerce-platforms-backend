package com.nyasha.store.indexing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class IndexManagementService {

    private final SearchIndexSyncService searchIndexSyncService;
    private final IndexingEventRepository indexingEventRepository;
    private final long backpressurePendingThreshold;
    private final long backpressureAgeMsThreshold;

    public IndexManagementService(
            SearchIndexSyncService searchIndexSyncService,
            IndexingEventRepository indexingEventRepository,
            @Value("${search.infrastructure.kafka.backpressure.pending-threshold:50}") long backpressurePendingThreshold,
            @Value("${search.infrastructure.kafka.backpressure.age-ms-threshold:120000}") long backpressureAgeMsThreshold
    ) {
        this.searchIndexSyncService = searchIndexSyncService;
        this.indexingEventRepository = indexingEventRepository;
        this.backpressurePendingThreshold = Math.max(1L, backpressurePendingThreshold);
        this.backpressureAgeMsThreshold = Math.max(1L, backpressureAgeMsThreshold);
    }

    public void rebuildIndex() {
        searchIndexSyncService.rebuildAll();
    }

    public IndexingStatusSnapshot getStatus() {
        long documentCount = searchIndexSyncService.documentCount();
        long pendingEvents = indexingEventRepository.countByStatus(IndexingEventStatus.PENDING);
        long failedEvents = indexingEventRepository.countByStatus(IndexingEventStatus.FAILED);
        long deadLetterEvents = indexingEventRepository.countByStatus(IndexingEventStatus.DEAD_LETTER);

        long oldestPendingEventAgeMs = indexingEventRepository
                .findTopByStatusInOrderByEventTimeAsc(List.of(IndexingEventStatus.PENDING, IndexingEventStatus.FAILED))
                .map(IndexingEvent::getEventTime)
                .map(time -> ChronoUnit.MILLIS.between(time, LocalDateTime.now()))
                .orElse(0L);

        boolean sustainedBackpressure = pendingEvents >= backpressurePendingThreshold
                && oldestPendingEventAgeMs >= backpressureAgeMsThreshold;

        String lastSuccessful = indexingEventRepository
                .findTopByStatusOrderByProcessedAtDesc(IndexingEventStatus.COMPLETED)
                .map(IndexingEvent::getProcessedAt)
                .map(Object::toString)
                .orElse("never");

        return new IndexingStatusSnapshot(
                documentCount,
                pendingEvents,
                failedEvents,
                deadLetterEvents,
                oldestPendingEventAgeMs,
                sustainedBackpressure,
                lastSuccessful
        );
    }
}
