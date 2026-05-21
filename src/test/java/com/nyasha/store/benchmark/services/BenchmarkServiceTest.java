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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BenchmarkServiceTest {

    @TempDir
    private Path reportDirectory;

    @Test
    void startRunExecutesAllEnginesAndPersistsReportArtifacts() throws Exception {
        BenchmarkQuerySetRepository querySetRepository = mock(BenchmarkQuerySetRepository.class);
        BenchmarkJudgmentRepository judgmentRepository = mock(BenchmarkJudgmentRepository.class);
        BenchmarkRunRepository runRepository = mock(BenchmarkRunRepository.class);
        BenchmarkResultRepository resultRepository = mock(BenchmarkResultRepository.class);
        ProductSearchService productSearchService = mock(ProductSearchService.class);
        IndexingEventRepository indexingEventRepository = mock(IndexingEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        BenchmarkQuerySet querySet = querySet();
        List<BenchmarkResult> savedResults = new ArrayList<>();
        AtomicReference<BenchmarkRun> savedRun = new AtomicReference<>();

        when(querySetRepository.findByName("electronics-basic")).thenReturn(Optional.of(querySet));
        when(judgmentRepository.findByQuerySetAndQueryTextIgnoreCase(eq(querySet), eq("laptop")))
                .thenReturn(List.of(judgment(querySet, "laptop", 1L, 3), judgment(querySet, "laptop", 2L, 2)));
        when(indexingEventRepository.findAll()).thenReturn(List.of());
        when(runRepository.save(any(BenchmarkRun.class))).thenAnswer(invocation -> {
            BenchmarkRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(42L);
            }
            savedRun.set(run);
            return run;
        });
        when(runRepository.findById(42L)).thenAnswer(invocation -> Optional.ofNullable(savedRun.get()));
        when(resultRepository.save(any(BenchmarkResult.class))).thenAnswer(invocation -> {
            BenchmarkResult result = invocation.getArgument(0);
            result.setId((long) savedResults.size() + 1L);
            savedResults.add(result);
            return result;
        });
        when(resultRepository.findByRunOrderByQueryTextAscEngineAsc(any(BenchmarkRun.class)))
                .thenReturn(savedResults);
        when(productSearchService.search(anyString(), eq("laptop"), eq(5))).thenAnswer(invocation -> {
            String engine = invocation.getArgument(0);
            return SearchResult.success(SearchEngineType.from(engine), 12L, List.of(product(1L), product(3L)));
        });

        BenchmarkService service = new BenchmarkService(
                querySetRepository,
                judgmentRepository,
                runRepository,
                resultRepository,
                productSearchService,
                indexingEventRepository,
                objectMapper,
                reportDirectory.toString()
        );

        var response = service.startRun(null, 5);

        assertThat(response.runId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(BenchmarkRunStatus.COMPLETED.name());
        assertThat(response.queryCount()).isEqualTo(1);
        assertThat(savedResults).hasSize(SearchEngineType.values().length);
        assertThat(savedResults)
                .allSatisfy(result -> {
                    assertThat(result.getPrecisionAtK()).isEqualTo(0.5);
                    assertThat(result.getRecallAtK()).isEqualTo(0.5);
                    assertThat(result.getMrrAtK()).isEqualTo(1.0);
                    assertThat(result.getNdcgAtK()).isGreaterThan(0.0);
                });
        assertThat(savedRun.get().getStatus()).isEqualTo(BenchmarkRunStatus.COMPLETED);
        assertThat(savedRun.get().getReportDirectory()).startsWith(reportDirectory.toString());
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "summary.md"))).isTrue();
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "results.json"))).isTrue();
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "latency.csv"))).isTrue();
        assertThat(Files.exists(Path.of(savedRun.get().getReportDirectory(), "relevance.csv"))).isTrue();
        for (SearchEngineType engineType : SearchEngineType.values()) {
            verify(productSearchService).search(engineType.canonical(), "laptop", 5);
        }
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
