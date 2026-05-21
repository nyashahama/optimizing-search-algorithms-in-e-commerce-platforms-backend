package com.nyasha.store.benchmark.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.benchmark.models.BenchmarkQuery;
import com.nyasha.store.benchmark.models.BenchmarkQuerySet;
import com.nyasha.store.benchmark.models.BenchmarkRun;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.repositories.BenchmarkJudgmentRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkQuerySetRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkResultRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkRunRepository;
import com.nyasha.store.indexing.IndexingEventRepository;
import com.nyasha.store.search.ProductSearchService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BenchmarkServiceTest {

    @Test
    void startRunQueuesBenchmarkAndReturnsQueuedResponse() {
        BenchmarkQuerySetRepository querySetRepository = mock(BenchmarkQuerySetRepository.class);
        BenchmarkJudgmentRepository judgmentRepository = mock(BenchmarkJudgmentRepository.class);
        BenchmarkRunRepository runRepository = mock(BenchmarkRunRepository.class);
        BenchmarkResultRepository resultRepository = mock(BenchmarkResultRepository.class);
        BenchmarkRunExecutorService executorService = mock(BenchmarkRunExecutorService.class);
        ProductSearchService productSearchService = mock(ProductSearchService.class);
        IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        BenchmarkQuerySet querySet = querySet();

        when(querySetRepository.findByName("electronics-basic")).thenReturn(Optional.of(querySet));
        AtomicReference<BenchmarkRun> savedRun = new AtomicReference<>();
        when(runRepository.save(any())).thenAnswer(invocation -> {
            BenchmarkRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(42L);
            }
            savedRun.set(run);
            return run;
        });

        BenchmarkService service = new BenchmarkService(
                querySetRepository,
                judgmentRepository,
                runRepository,
                resultRepository,
                executorService,
                productSearchService,
                indexingEventRepository,
                objectMapper,
                "target/reports"
        );

        var response = service.startRun(null, 5);

        assertThat(response.status()).isEqualTo(BenchmarkRunStatus.QUEUED.name());
        assertThat(response.queryCount()).isEqualTo(1);
        verify(executorService).runAsync(savedRun.get().getId(), querySet.getId(), 5);
    }

    @Test
    void startRunDefaultsLimitWhenInputIsInvalid() {
        BenchmarkQuerySetRepository querySetRepository = mock(BenchmarkQuerySetRepository.class);
        BenchmarkJudgmentRepository judgmentRepository = mock(BenchmarkJudgmentRepository.class);
        BenchmarkRunRepository runRepository = mock(BenchmarkRunRepository.class);
        BenchmarkResultRepository resultRepository = mock(BenchmarkResultRepository.class);
        BenchmarkRunExecutorService executorService = mock(BenchmarkRunExecutorService.class);
        ProductSearchService productSearchService = mock(ProductSearchService.class);
        IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        BenchmarkQuerySet querySet = new BenchmarkQuerySet();
        querySet.setId(77L);
        querySet.setName("electronics-basic");

        when(querySetRepository.findByName("electronics-basic")).thenReturn(Optional.of(querySet));
        AtomicReference<BenchmarkRun> savedRun = new AtomicReference<>();
        when(runRepository.save(any())).thenAnswer(invocation -> {
            BenchmarkRun run = invocation.getArgument(0);
            run.setId(7L);
            savedRun.set(run);
            return run;
        });

        BenchmarkService service = new BenchmarkService(
                querySetRepository,
                judgmentRepository,
                runRepository,
                resultRepository,
                executorService,
                productSearchService,
                indexingEventRepository,
                objectMapper,
                "target/reports"
        );

        var response = service.startRun(null, null);

        assertThat(response.status()).isEqualTo(BenchmarkRunStatus.QUEUED.name());
        assertThat(response.queryCount()).isZero();
        verify(executorService).runAsync(savedRun.get().getId(), querySet.getId(), 20);
    }

    private BenchmarkQuerySet querySet() {
        BenchmarkQuerySet querySet = new BenchmarkQuerySet();
        querySet.setId(7L);
        querySet.setName("electronics-basic");

        BenchmarkQuery query = new BenchmarkQuery();
        query.setId(8L);
        query.setQuerySet(querySet);
        query.setQueryText("laptop");
        query.setPosition(1);
        querySet.getQueries().add(query);

        return querySet;
    }

}
