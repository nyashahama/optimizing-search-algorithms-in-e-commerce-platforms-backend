package com.nyasha.store.observability.health;

import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.repositories.BenchmarkRunRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BenchmarkQueueHealthIndicator implements HealthIndicator {

    private static final int BACKLOG_WARNING_THRESHOLD = 2;
    private static final int FAILED_WARNING_THRESHOLD = 5;

    private final BenchmarkRunRepository benchmarkRunRepository;

    public BenchmarkQueueHealthIndicator(BenchmarkRunRepository benchmarkRunRepository) {
        this.benchmarkRunRepository = benchmarkRunRepository;
    }

    @Override
    public Health health() {
        try {
            long queued = benchmarkRunRepository.countByStatus(BenchmarkRunStatus.QUEUED);
            long running = benchmarkRunRepository.countByStatus(BenchmarkRunStatus.RUNNING);
            long failed = benchmarkRunRepository.countByStatus(BenchmarkRunStatus.FAILED);
            long concurrentWorkload = queued + running;

            Health.Builder builder = Health.up()
                    .withDetail("queuedRuns", queued)
                    .withDetail("runningRuns", running)
                    .withDetail("failedRuns", failed)
                    .withDetail("concurrentWorkload", concurrentWorkload);

            if (failed >= FAILED_WARNING_THRESHOLD) {
                return Health.down().withDetails(builder.build().getDetails())
                        .withDetail("message", "Benchmark queue health degraded with repeated failures")
                        .build();
            }

            if (concurrentWorkload > BACKLOG_WARNING_THRESHOLD) {
                return Health.outOfService().withDetails(builder.build().getDetails())
                        .withDetail("message", "Benchmark queue workload is high")
                        .build();
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
