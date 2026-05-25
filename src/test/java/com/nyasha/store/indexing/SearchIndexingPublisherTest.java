package com.nyasha.store.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexingPublisherTest {

    private final IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishMarksEventPendingOnSuccessfulSend() {
        SearchIndexingPublisher publisher = new SearchIndexingPublisher(
                indexingEventRepository,
                kafkaTemplate,
                objectMapper,
                "product-index-events",
                4,
                1,
                0L,
                200L
        );

        IndexingEvent event = event("evt-success");
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("product-index-events"), eq("evt-success"), any(String.class)))
                .thenReturn(future);
        when(indexingEventRepository.save(any(IndexingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        publisher.publish(event);

        assertThat(event.getStatus()).isEqualTo(IndexingEventStatus.PENDING);
        assertThat(event.getErrorMessage()).isNull();
        verify(indexingEventRepository, atLeastOnce()).save(event);
    }

    @Test
    void publishMarksEventFailedAndIncrementsRetryCountAfterRetriesExhausted() {
        SearchIndexingPublisher publisher = new SearchIndexingPublisher(
                indexingEventRepository,
                kafkaTemplate,
                objectMapper,
                "product-index-events",
                4,
                2,
                0L,
                200L
        );

        IndexingEvent event = event("evt-fail");
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq("product-index-events"), eq("evt-fail"), any(String.class)))
                .thenReturn(failedFuture);
        when(indexingEventRepository.save(any(IndexingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish indexing event after producer retries");

        assertThat(event.getStatus()).isEqualTo(IndexingEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).contains("Index event publish failed");
        verify(indexingEventRepository, atLeastOnce()).save(event);
    }

    private IndexingEvent event(String eventId) {
        IndexingEvent event = new IndexingEvent();
        event.setEventId(eventId);
        event.setProductId(1L);
        event.setEventType(IndexingEventType.UPSERT);
        event.setStatus(IndexingEventStatus.PENDING);
        event.setRetryCount(0);
        event.setPayload("{\"productId\":1}");
        return event;
    }
}
