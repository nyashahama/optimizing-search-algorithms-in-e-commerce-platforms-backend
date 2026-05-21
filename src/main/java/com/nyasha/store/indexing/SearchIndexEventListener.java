package com.nyasha.store.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SearchIndexEventListener {

    private final IndexingEventRepository indexingEventRepository;
    private final SearchIndexSyncService searchIndexSyncService;
    private final SearchIndexingPublisher searchIndexingPublisher;
    private final ObjectMapper objectMapper;

    public SearchIndexEventListener(
            IndexingEventRepository indexingEventRepository,
            SearchIndexSyncService searchIndexSyncService,
            SearchIndexingPublisher searchIndexingPublisher,
            ObjectMapper objectMapper
    ) {
        this.indexingEventRepository = indexingEventRepository;
        this.searchIndexSyncService = searchIndexSyncService;
        this.searchIndexingPublisher = searchIndexingPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${search.infrastructure.kafka.product-event-topic:product-index-events}",
            groupId = "${search.infrastructure.kafka.consumer-group-id:indexer-group}")
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) {
        IndexingEventMessage message = fromJson(record.value());
        if (message == null || message.eventId() == null || message.eventType() == null) {
            return;
        }

        IndexingEvent event = indexingEventRepository.findByEventId(message.eventId())
                .orElse(null);
        if (event == null) {
            return;
        }

        event.setStatus(IndexingEventStatus.PROCESSING);
        indexingEventRepository.save(event);

        try {
            switch (message.eventType()) {
                case UPSERT -> searchIndexSyncService.indexProduct(message.productId());
                case DELETE -> searchIndexSyncService.deleteProduct(message.productId());
            }

            event.setStatus(IndexingEventStatus.COMPLETED);
            event.setProcessedAt(LocalDateTime.now());
            event.setErrorMessage(null);
        } catch (Exception e) {
            event.setErrorMessage(e.getMessage());
            event.setRetryCount(event.getRetryCount() == null ? 1 : event.getRetryCount() + 1);

            if (searchIndexingPublisher.shouldRetry(event)) {
                event.setStatus(IndexingEventStatus.FAILED);
            } else {
                event.setStatus(IndexingEventStatus.DEAD_LETTER);
                event.setErrorMessage(
                        "Index event exceeded max retries (%s): %s".formatted(
                                searchIndexingPublisher.getMaxRetries(),
                                e.getMessage()
                        )
                );
            }
        }

        indexingEventRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${search.infrastructure.kafka.retry-delay-ms:30000}")
    @Transactional
    public void retryFailedIndexEvents() {
        List<IndexingEvent> failedEvents = indexingEventRepository.findTop200ByStatusInOrderByEventTimeAsc(
                List.of(IndexingEventStatus.FAILED, IndexingEventStatus.PENDING)
        );

        for (IndexingEvent event : failedEvents) {
            if (event.getEventId() == null) {
                continue;
            }

            if (!searchIndexingPublisher.shouldRetry(event)) {
                event.setStatus(IndexingEventStatus.DEAD_LETTER);
                event.setErrorMessage("Index event exceeded max retries (" + searchIndexingPublisher.getMaxRetries() + ")");
                indexingEventRepository.save(event);
                continue;
            }

            int nextRetryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            try {
                searchIndexingPublisher.publish(event);
                event.setRetryCount(nextRetryCount);
                event.setStatus(IndexingEventStatus.PENDING);
            } catch (Exception e) {
                event.setRetryCount(nextRetryCount);
                event.setErrorMessage("Index event publish failed: " + e.getMessage());
                event.setStatus(IndexingEventStatus.FAILED);
            }
            indexingEventRepository.save(event);
        }
    }

    private IndexingEventMessage fromJson(String value) {
        try {
            return objectMapper.readValue(value, IndexingEventMessage.class);
        } catch (Exception e) {
            return null;
        }
    }
}
