package com.nyasha.store.benchmark.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.benchmark.models.BenchmarkJudgment;
import com.nyasha.store.benchmark.models.BenchmarkQuery;
import com.nyasha.store.benchmark.models.BenchmarkQuerySet;
import com.nyasha.store.benchmark.models.BenchmarkResult;
import com.nyasha.store.benchmark.models.BenchmarkRun;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.repositories.BenchmarkJudgmentRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkQuerySetRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkResultRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkRunRepository;
import com.nyasha.store.entities.Product;
import com.nyasha.store.indexing.IndexingEventRepository;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.search.SearchEngineType;
import com.nyasha.store.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkRunExecutorServiceTest {

    @TempDir
    private Path reportDirectory;

    @Test
    void runAsyncCompletesBenchmarkAndWritesArtifacts() throws Exception {
        BenchmarkQuerySetRepository querySetRepository = mock(BenchmarkQuerySetRepository.class);
        BenchmarkJudgmentRepository judgmentRepository = mock(BenchmarkJudgmentRepository.class);
        BenchmarkRunRepository runRepository = mock(BenchmarkRunRepository.class);
        BenchmarkResultRepository resultRepository = mock(BenchmarkResultRepository.class);
        ProductSearchService productSearchService = mock(ProductSearchService.class);
        IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        BenchmarkQuerySet querySet = querySet();
        BenchmarkRun run = run(querySet);
        List<BenchmarkResult> savedResults = new ArrayList<>();
        AtomicReference<BenchmarkRun> savedRun = new AtomicReference<>();

        when(querySetRepository.findById(7L)).thenReturn(Optional.of(querySet));
        when(judgmentRepository.findByQuerySetAndQueryTextIgnoreCase(eq(querySet), eq("laptop"))).thenReturn(List.of(
                judgment(querySet, "laptop", 1L, 3),
                judgment(querySet, "laptop", 2L, 2)
        ));
        when(indexingEventRepository.findAll()).thenReturn(List.of());
        when(runRepository.findById(42L)).thenReturn(Optional.of(run));
        when(runRepository.save(any(BenchmarkRun.class))).thenAnswer(invocation -> {
            BenchmarkRun value = invocation.getArgument(0);
            run.setId(value.getId());
            run.setStatus(value.getStatus());
            run.setStartedAt(value.getStartedAt());
            run.setCompletedAt(value.getCompletedAt());
            run.setDurationMs(value.getDurationMs());
            run.setThroughputQueriesPerSecond(value.getThroughputQueriesPerSecond());
            run.setLatencyMinMs(value.getLatencyMinMs());
            run.setLatencyP50Ms(value.getLatencyP50Ms());
            run.setLatencyP95Ms(value.getLatencyP95Ms());
            run.setLatencyP99Ms(value.getLatencyP99Ms());
            run.setLatencyAvgMs(value.getLatencyAvgMs());
            run.setFreshnessP50Ms(value.getFreshnessP50Ms());
            run.setFreshnessP95Ms(value.getFreshnessP95Ms());
            run.setFreshnessP99Ms(value.getFreshnessP99Ms());
            run.setFreshnessAvgMs(value.getFreshnessAvgMs());
            run.setReportDirectory(value.getReportDirectory());
            savedRun.set(run);
            return value;
        });
        when(resultRepository.save(any(BenchmarkResult.class))).thenAnswer(invocation -> {
            BenchmarkResult result = invocation.getArgument(0);
            result.setId((long) (savedResults.size() + 1));
            savedResults.add(result);
            return result;
        });
        when(resultRepository.findByRunOrderByQueryTextAscEngineAsc(any(BenchmarkRun.class))).thenReturn(savedResults);
        when(productSearchService.search(anyString(), eq("laptop"), eq(5))).thenAnswer(invocation ->
                SearchResult.success(SearchEngineType.from(invocation.getArgument(0)), 12L, List.of(product(1L), product(3L)))
        );

        BenchmarkRunExecutorService service = new BenchmarkRunExecutorService(
                querySetRepository,
                judgmentRepository,
                runRepository,
                resultRepository,
                productSearchService,
                indexingEventRepository,
                objectMapper,
                reportDirectory.toString()
        );

        service.runAsync(42L, 7L, 5);

        assertThat(savedRun.get()).isNotNull();
        assertThat(savedRun.get().getStatus()).isEqualTo(BenchmarkRunStatus.COMPLETED);
        assertThat(savedRun.get().getReportDirectory()).startsWith(reportDirectory.toString());
        assertThat(savedResults).hasSize(SearchEngineType.values().length);
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "summary.md"))).isTrue();
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "results.json"))).isTrue();
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "latency.csv"))).isTrue();
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "relevance.csv"))).isTrue();

        assertThat(savedRun.get().getLatencyAvgMs()).isNotNull();
        assertThat(savedRun.get().getThroughputQueriesPerSecond()).isNotNull();
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

    private BenchmarkRun run(BenchmarkQuerySet querySet) {
        BenchmarkRun run = new BenchmarkRun();
        run.setId(42L);
        run.setQuerySet(querySet);
        run.setStatus(BenchmarkRunStatus.QUEUED);
        return run;
    }

    private BenchmarkJudgment judgment(BenchmarkQuerySet querySet, String queryText, Long productId, Integer relevance) {
        BenchmarkJudgment judgment = new BenchmarkJudgment();
        judgment.setQuerySet(querySet);
        judgment.setQueryText(queryText);
        judgment.setProductId(productId);
        judgment.setRelevance(relevance);
        return judgment;
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setProductId(id);
        product.setName("Product " + id);
        return product;
    }
}

