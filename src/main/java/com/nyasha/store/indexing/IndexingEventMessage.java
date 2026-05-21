package com.nyasha.store.indexing;

public record IndexingEventMessage(
        String eventId,
        Long productId,
        IndexingEventType eventType,
        String payload
) {
}
