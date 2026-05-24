package com.nyasha.store.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsControllerTest {

    private final OperationalStatusService operationalStatusService = mock(OperationalStatusService.class);
    private final OperationsController operationsController = new OperationsController(operationalStatusService);

    @Test
    void returnsStatusPayloadFromService() {
        OperationalStatusSnapshot snapshot = new OperationalStatusSnapshot(
                "UP",
                "2026-05-22T00:00:00Z",
                "green",
                true,
                100L,
                0L,
                0L,
                0L,
                false,
                "never",
                0L,
                0L,
                0L,
                List.of()
        );
        when(operationalStatusService.getStatus()).thenReturn(snapshot);

        ResponseEntity<OperationalStatusSnapshot> response = operationsController.status();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().overallStatus()).isEqualTo("UP");
    }
}
