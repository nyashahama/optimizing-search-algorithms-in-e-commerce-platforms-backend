package com.nyasha.store.indexing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.entities.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Component
public class SearchIndexingPublisher {

    private static final int DEFAULT_MAX_RETRIES = 4;
    private static final int DEFAULT_PRODUCER_RETRIES = 3;
    private static final long DEFAULT_PRODUCER_RETRY_DELAY_MS = 500L;

    private final IndexingEventRepository indexingEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;
    private final int maxRetries;
    private final int producerRetryAttempts;
    private final long producerRetryDelayMs;

    public SearchIndexingPublisher(
            IndexingEventRepository indexingEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${search.infrastructure.kafka.product-event-topic:product-index-events}") String topicName,
            @Value("${search.infrastructure.kafka.max-retries:" + DEFAULT_MAX_RETRIES + "}") int maxRetries,
            @Value("${search.infrastructure.kafka.producer.retry-attempts:" + DEFAULT_PRODUCER_RETRIES + "}") int producerRetryAttempts,
            @Value("${search.infrastructure.kafka.producer.retry-delay-ms:" + DEFAULT_PRODUCER_RETRY_DELAY_MS + "}") long producerRetryDelayMs
    ) {
        this.indexingEventRepository = indexingEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicName = topicName;
        this.maxRetries = Math.max(1, maxRetries);
        this.producerRetryAttempts = Math.max(1, producerRetryAttempts);
        this.producerRetryDelayMs = Math.max(0L, producerRetryDelayMs);
    }

    public IndexingEvent publish(Product product, IndexingEventType eventType) {
        IndexingEvent event = createEvent(product.getProductId(), eventType, payloadFromProduct(product));
        publish(event);
        return event;
    }

    public IndexingEvent publishDelete(Long productId) {
        IndexingEvent event = createEvent(productId, IndexingEventType.DELETE, "{\"productId\":" + productId + "}");
        publish(event);
        return event;
    }

    private IndexingEvent createEvent(Long productId, IndexingEventType eventType, String payload) {
        IndexingEvent event = new IndexingEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setProductId(productId);
        event.setEventType(eventType);
        event.setStatus(IndexingEventStatus.PENDING);
        event.setEventTime(LocalDateTime.now());
        event.setRetryCount(0);
        event.setPayload(payload);
        return indexingEventRepository.save(event);
    }

    public void publish(IndexingEvent event) {
        if (event == null || event.getEventId() == null) {
            return;
        }

        IndexingEventMessage message = new IndexingEventMessage(
                event.getEventId(),
                event.getProductId(),
                event.getEventType(),
                event.getPayload()
        );
        String payload = asJson(message);

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= producerRetryAttempts; attempt++) {
            try {
                kafkaTemplate.send(topicName, event.getEventId(), payload)
                        .get(5, TimeUnit.SECONDS);
                event.setStatus(IndexingEventStatus.PENDING);
                indexingEventRepository.save(event);
                return;
            } catch (Exception e) {
                lastFailure = e;
                if (attempt >= producerRetryAttempts) {
                    break;
                }
                sleepBetweenRetries(attempt);
            }
        }

        throw new IllegalStateException("Failed to publish indexing event after producer retries", lastFailure);
    }

    public boolean shouldRetry(IndexingEvent event) {
        if (event == null || event.getRetryCount() == null) {
            return false;
        }
        return event.getRetryCount() < maxRetries;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    private String payloadFromProduct(Product product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String asJson(IndexingEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void sleepBetweenRetries(int attempt) {
        if (producerRetryDelayMs <= 0L) {
            return;
        }

        try {
            Thread.sleep(producerRetryDelayMs * attempt);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
