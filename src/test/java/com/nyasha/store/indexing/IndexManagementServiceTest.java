package com.nyasha.store.indexing;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexManagementServiceTest {

    private final SearchIndexSyncService searchIndexSyncService = mock(SearchIndexSyncService.class);
    private final IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);

    @Test
    void getStatusIncludesCountsBackpressureAndLastSuccessfulIndexTime() {
        IndexManagementService service = new IndexManagementService(
                searchIndexSyncService,
                indexingEventRepository,
                2L,
                1000L
        );
        LocalDateTime oldPendingTime = LocalDateTime.now().minusSeconds(5);
        LocalDateTime completedAt = LocalDateTime.now().minusSeconds(1);
        when(searchIndexSyncService.documentCount()).thenReturn(40L);
        when(indexingEventRepository.countByStatus(IndexingEventStatus.PENDING)).thenReturn(2L);
        when(indexingEventRepository.countByStatus(IndexingEventStatus.FAILED)).thenReturn(1L);
        when(indexingEventRepository.countByStatus(IndexingEventStatus.DEAD_LETTER)).thenReturn(3L);
        when(indexingEventRepository.findTopByStatusInOrderByEventTimeAsc(List.of(IndexingEventStatus.PENDING, IndexingEventStatus.FAILED)))
                .thenReturn(Optional.of(event(IndexingEventStatus.PENDING, oldPendingTime, null)));
        when(indexingEventRepository.findTopByStatusOrderByProcessedAtDesc(IndexingEventStatus.COMPLETED))
                .thenReturn(Optional.of(event(IndexingEventStatus.COMPLETED, oldPendingTime, completedAt)));

        IndexingStatusSnapshot status = service.getStatus();

        assertThat(status.openSearchDocumentCount()).isEqualTo(40L);
        assertThat(status.pendingEvents()).isEqualTo(2L);
        assertThat(status.failedEvents()).isEqualTo(1L);
        assertThat(status.deadLetterEvents()).isEqualTo(3L);
        assertThat(status.oldestPendingEventAgeMs()).isGreaterThanOrEqualTo(1000L);
        assertThat(status.sustainedBackpressure()).isTrue();
        assertThat(status.lastSuccessfulIndexTime()).isEqualTo(completedAt.toString());
    }

    @Test
    void rebuildIndexDelegatesToSyncService() {
        IndexManagementService service = new IndexManagementService(searchIndexSyncService, indexingEventRepository, 50L, 120000L);

        service.rebuildIndex();

        verify(searchIndexSyncService).rebuildAll();
    }

    @Test
    void rebuildIndexSkipsFailureWhenSearchBackendIsUnavailable() {
        doThrow(new IllegalStateException("OpenSearch unavailable")).when(searchIndexSyncService).rebuildAll();
        IndexManagementService service = new IndexManagementService(searchIndexSyncService, indexingEventRepository, 50L, 120000L);

        service.rebuildIndex();

        verify(searchIndexSyncService).rebuildAll();
    }

    private IndexingEvent event(IndexingEventStatus status, LocalDateTime eventTime, LocalDateTime processedAt) {
        IndexingEvent event = new IndexingEvent();
        event.setEventId("event-" + status);
        event.setProductId(1L);
        event.setEventType(IndexingEventType.UPSERT);
        event.setStatus(status);
        event.setEventTime(eventTime);
        event.setProcessedAt(processedAt);
        event.setRetryCount(0);
        return event;
    }
}
