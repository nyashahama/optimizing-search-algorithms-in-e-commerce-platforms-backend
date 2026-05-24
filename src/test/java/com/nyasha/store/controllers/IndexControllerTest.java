package com.nyasha.store.controllers;

import com.nyasha.store.indexing.IndexManagementService;
import com.nyasha.store.indexing.IndexingStatusSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexControllerTest {

    private final IndexManagementService indexManagementService = mock(IndexManagementService.class);
    private final IndexController indexController = new IndexController(indexManagementService);

    @Test
    void rebuildMarksAcceptedAndCallsService() {
        ResponseEntity<Void> response = indexController.rebuild();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(indexManagementService).rebuildIndex();
    }

    @Test
    void statusReturnsSnapshot() {
        IndexingStatusSnapshot snapshot = new IndexingStatusSnapshot(
                3L, 0L, 0L, 0L, 0L, false, "now"
        );
        when(indexManagementService.getStatus()).thenReturn(snapshot);

        assertThat(indexController.status().getBody()).isEqualTo(snapshot);
    }
}
