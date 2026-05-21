package com.nyasha.store.indexing;

public record IndexingStatusSnapshot(
        long openSearchDocumentCount,
        long pendingEvents,
        long failedEvents,
        long deadLetterEvents,
        long oldestPendingEventAgeMs,
        boolean sustainedBackpressure,
        String lastSuccessfulIndexTime
) {
}
