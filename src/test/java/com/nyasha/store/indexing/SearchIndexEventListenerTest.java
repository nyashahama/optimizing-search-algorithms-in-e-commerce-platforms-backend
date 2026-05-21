package com.nyasha.store.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexEventListenerTest {

    private final IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);
    private final SearchIndexSyncService searchIndexSyncService = mock(SearchIndexSyncService.class);
    private final SearchIndexingPublisher searchIndexingPublisher = mock(SearchIndexingPublisher.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchIndexEventListener listener = new SearchIndexEventListener(
            indexingEventRepository,
            searchIndexSyncService,
            searchIndexingPublisher,
            objectMapper
    );

    @Test
    void onMessageIndexesProductAndMarksEventCompleted() throws Exception {
        IndexingEvent event = event("event-1", 10L, IndexingEventType.UPSERT, 0);
        when(indexingEventRepository.findByEventId("event-1")).thenReturn(Optional.of(event));

        listener.onMessage(record(new IndexingEventMessage("event-1", 10L, IndexingEventType.UPSERT, "{}")));

        verify(searchIndexSyncService).indexProduct(10L);
        assertThat(event.getStatus()).isEqualTo(IndexingEventStatus.COMPLETED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getErrorMessage()).isNull();
        verify(indexingEventRepository, times(2)).save(event);
    }

    @Test
    void onMessageDeletesProductForDeleteEvents() throws Exception {
        IndexingEvent event = event("event-2", 11L, IndexingEventType.DELETE, 0);
        when(indexingEventRepository.findByEventId("event-2")).thenReturn(Optional.of(event));

        listener.onMessage(record(new IndexingEventMessage("event-2", 11L, IndexingEventType.DELETE, "{}")));

        verify(searchIndexSyncService).deleteProduct(11L);
        assertThat(event.getStatus()).isEqualTo(IndexingEventStatus.COMPLETED);
    }

    @Test
    void onMessageDeadLettersEventWhenRetryBudgetIsExceeded() throws Exception {
        IndexingEvent event = event("event-3", 12L, IndexingEventType.UPSERT, 3);
        when(indexingEventRepository.findByEventId("event-3")).thenReturn(Optional.of(event));
        when(searchIndexingPublisher.shouldRetry(event)).thenReturn(false);
        when(searchIndexingPublisher.getMaxRetries()).thenReturn(4);
        org.mockito.Mockito.doThrow(new IllegalStateException("OpenSearch unavailable"))
                .when(searchIndexSyncService)
                .indexProduct(12L);

        listener.onMessage(record(new IndexingEventMessage("event-3", 12L, IndexingEventType.UPSERT, "{}")));

        assertThat(event.getStatus()).isEqualTo(IndexingEventStatus.DEAD_LETTER);
        assertThat(event.getRetryCount()).isEqualTo(4);
        assertThat(event.getErrorMessage()).contains("exceeded max retries");
        verify(indexingEventRepository, times(2)).save(event);
    }

    private ConsumerRecord<String, String> record(IndexingEventMessage message) throws Exception {
        return new ConsumerRecord<>(
                "product-index-events",
                0,
                0L,
                message.eventId(),
                objectMapper.writeValueAsString(message)
        );
    }

    private IndexingEvent event(String eventId, Long productId, IndexingEventType eventType, Integer retryCount) {
        IndexingEvent event = new IndexingEvent();
        event.setEventId(eventId);
        event.setProductId(productId);
        event.setEventType(eventType);
        event.setStatus(IndexingEventStatus.PENDING);
        event.setEventTime(LocalDateTime.now());
        event.setRetryCount(retryCount);
        event.setPayload("{}");
        return event;
    }
}
