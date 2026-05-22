package com.nyasha.store.observability;

import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.repositories.BenchmarkRunRepository;
import com.nyasha.store.observability.health.BenchmarkQueueHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkQueueHealthIndicatorTest {

    @Test
    void reportsOutOfServiceWhenWorkloadIsHigh() {
        BenchmarkRunRepository repository = mock(BenchmarkRunRepository.class);
        BenchmarkQueueHealthIndicator indicator = new BenchmarkQueueHealthIndicator(repository);

        when(repository.countByStatus(BenchmarkRunStatus.QUEUED)).thenReturn(2L);
        when(repository.countByStatus(BenchmarkRunStatus.RUNNING)).thenReturn(1L);
        when(repository.countByStatus(BenchmarkRunStatus.FAILED)).thenReturn(0L);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("OUT_OF_SERVICE");
    }

    @Test
    void downWhenFailuresAreRepeated() {
        BenchmarkRunRepository repository = mock(BenchmarkRunRepository.class);
        BenchmarkQueueHealthIndicator indicator = new BenchmarkQueueHealthIndicator(repository);

        when(repository.countByStatus(BenchmarkRunStatus.QUEUED)).thenReturn(0L);
        when(repository.countByStatus(BenchmarkRunStatus.RUNNING)).thenReturn(0L);
        when(repository.countByStatus(BenchmarkRunStatus.FAILED)).thenReturn(5L);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
    }
}
