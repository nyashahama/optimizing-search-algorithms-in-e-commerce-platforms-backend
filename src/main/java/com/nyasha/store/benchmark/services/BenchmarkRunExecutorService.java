package com.nyasha.store.benchmark.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyasha.store.benchmark.dtos.BenchmarkResultDto;
import com.nyasha.store.benchmark.dtos.BenchmarkRunSummaryDto;
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
import com.nyasha.store.indexing.IndexingEventRepository;
import com.nyasha.store.indexing.IndexingEventStatus;
import com.nyasha.store.search.ProductSearchService;
import com.nyasha.store.search.SearchEngineType;
import com.nyasha.store.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

@Service
public class BenchmarkRunExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunExecutorService.class);
    private static final int DEFAULT_QUERY_LIMIT = 20;
    private static final int METRIC_K = 10;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");

    private final BenchmarkQuerySetRepository querySetRepository;
    private final BenchmarkJudgmentRepository judgmentRepository;
    private final BenchmarkRunRepository runRepository;
    private final BenchmarkResultRepository resultRepository;
    private final ProductSearchService productSearchService;
    private final IndexingEventRepository indexingEventRepository;
    private final ObjectMapper objectMapper;
    private final String reportDirectoryBase;

    public BenchmarkRunExecutorService(
            BenchmarkQuerySetRepository querySetRepository,
            BenchmarkJudgmentRepository judgmentRepository,
            BenchmarkRunRepository runRepository,
            BenchmarkResultRepository resultRepository,
            ProductSearchService productSearchService,
            IndexingEventRepository indexingEventRepository,
            ObjectMapper objectMapper,
            @Value("${benchmark.reports.base-directory:reports}") String reportDirectoryBase
    ) {
        this.querySetRepository = querySetRepository;
        this.judgmentRepository = judgmentRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.productSearchService = productSearchService;
        this.indexingEventRepository = indexingEventRepository;
        this.objectMapper = objectMapper;
        this.reportDirectoryBase = reportDirectoryBase;
    }

    @Async("benchmarkTaskExecutor")
    public void runAsync(Long runId, Long querySetId, Integer limit) {
        if (runId == null || querySetId == null) {
            throw new IllegalArgumentException("Benchmark run id and query set id are required");
        }

        BenchmarkRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Benchmark run not found"));
        BenchmarkQuerySet querySet = querySetRepository.findById(querySetId)
                .orElseThrow(() -> new IllegalArgumentException("Query set not found"));

        int normalizedLimit = limit == null || limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, 100);
        List<BenchmarkQuery> sortedQueries = querySet.getQueries().stream()
                .sorted(comparing(BenchmarkQuery::getPosition))
                .toList();
        long startedAtNanos = System.nanoTime();
        List<Long> supportedLatencies = new ArrayList<>();

        try {
            run.setStatus(BenchmarkRunStatus.RUNNING);
            run.setStartedAt(run.getStartedAt() == null ? LocalDateTime.now() : run.getStartedAt());
            runRepository.save(run);

            for (BenchmarkQuery query : sortedQueries) {
                String queryText = query.getQueryText();
                for (SearchEngineType engine : SearchEngineType.values()) {
                    SearchResult result = productSearchService.search(engine.canonical(), queryText, normalizedLimit);
                    BenchmarkResult benchmarkResult = mapResult(run, queryText, result, engine);
                    attachRelevanceMetrics(benchmarkResult, querySet, queryText);
                    resultRepository.save(benchmarkResult);
                    if (result.supported()) {
                        supportedLatencies.add(result.elapsedMs());
                    }
                }
            }

            run.setStatus(BenchmarkRunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs(toMs(System.nanoTime() - startedAtNanos));
            run.setThroughputQueriesPerSecond(computeThroughput(run, sortedQueries.size()));
            applyLatencyMetrics(run, supportedLatencies);
            applyFreshnessMetrics(run);
            run.setReportDirectory(resolveReportDirectory(run).toString());
            run = runRepository.save(run);

            persistArtifacts(run);
        } catch (Exception e) {
            run.setStatus(BenchmarkRunStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs(toMs(System.nanoTime() - startedAtNanos));
            runRepository.save(run);
            logger.error("Benchmark run {} failed", run.getId(), e);
            throw e;
        }
    }

    private BenchmarkResult mapResult(BenchmarkRun run, String queryText, SearchResult result, SearchEngineType engine) {
        BenchmarkResult benchmarkResult = new BenchmarkResult();
        benchmarkResult.setRun(run);
        benchmarkResult.setQueryText(queryText);
        benchmarkResult.setEngine(engine);
        benchmarkResult.setLatencyMs(result.elapsedMs());
        benchmarkResult.setResultCount(result.count());
        benchmarkResult.setReturnedCount(Math.min(result.count(), METRIC_K));
        benchmarkResult.setTopResultProductIds(
                result.products().stream()
                        .limit(METRIC_K)
                        .map(product -> String.valueOf(product.getProductId()))
                        .collect(Collectors.joining(","))
        );
        if (!result.supported()) {
            benchmarkResult.setErrorMessage(result.errorMessage());
        }
        return benchmarkResult;
    }

    private void attachRelevanceMetrics(BenchmarkResult benchmarkResult, BenchmarkQuerySet querySet, String queryText) {
        List<BenchmarkJudgment> judgments =
                judgmentRepository.findByQuerySetAndQueryTextIgnoreCase(querySet, queryText);

        if (judgments.isEmpty()) {
            return;
        }

        Map<Long, Integer> relevanceByProduct = judgments.stream()
                .collect(Collectors.toMap(BenchmarkJudgment::getProductId, BenchmarkJudgment::getRelevance, Math::max));

        List<Long> returnedProductIds = resultToProductIds(benchmarkResult);
        List<Long> relevantProductIds = returnedProductIds.stream()
                .filter(relevanceByProduct::containsKey)
                .toList();

        int relevantTotal = relevanceByProduct.size();
        int relevantInTopK = relevantProductIds.size();
        int k = Math.max(1, Math.min(METRIC_K, returnedProductIds.size()));

        double precisionAtK = k == 0 ? 0.0 : (double) relevantInTopK / k;
        double recallAtK = relevantTotal == 0 ? 0.0 : (double) relevantInTopK / relevantTotal;
        double mrrAtK = computeMrr(returnedProductIds, relevanceByProduct);
        double ndcgAtK = computeNdcg(returnedProductIds, relevanceByProduct);

        benchmarkResult.setPrecisionAtK(precisionAtK);
        benchmarkResult.setRecallAtK(recallAtK);
        benchmarkResult.setMrrAtK(mrrAtK);
        benchmarkResult.setNdcgAtK(ndcgAtK);
    }

    private List<Long> resultToProductIds(BenchmarkResult benchmarkResult) {
        if (benchmarkResult.getTopResultProductIds() == null || benchmarkResult.getTopResultProductIds().isBlank()) {
            return List.of();
        }

        return List.of(benchmarkResult.getTopResultProductIds().split(","))
                .stream()
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .map(Long::valueOf)
                .toList();
    }

    private void applyLatencyMetrics(BenchmarkRun run, List<Long> supportedLatencies) {
        if (supportedLatencies.isEmpty()) {
            run.setLatencyMinMs(0L);
            run.setLatencyP50Ms(0L);
            run.setLatencyP95Ms(0L);
            run.setLatencyP99Ms(0L);
            run.setLatencyAvgMs(0.0);
            return;
        }

        List<Long> latencies = new ArrayList<>(supportedLatencies);
        latencies.sort(Comparator.naturalOrder());
        run.setLatencyMinMs(latencies.get(0));
        run.setLatencyP50Ms(percentile(latencies, 50));
        run.setLatencyP95Ms(percentile(latencies, 95));
        run.setLatencyP99Ms(percentile(latencies, 99));
        run.setLatencyAvgMs(latencies.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private void applyFreshnessMetrics(BenchmarkRun run) {
        List<Long> freshnessDeltas = indexingEventRepository.findAll().stream()
                .filter(event -> event.getStatus() == IndexingEventStatus.COMPLETED)
                .filter(event -> event.getEventTime() != null)
                .filter(event -> event.getProcessedAt() != null)
                .map(event -> Duration.between(event.getEventTime(), event.getProcessedAt()).toMillis())
                .filter(delta -> delta >= 0)
                .sorted()
                .toList();

        if (freshnessDeltas.isEmpty()) {
            run.setFreshnessP50Ms(0L);
            run.setFreshnessP95Ms(0L);
            run.setFreshnessP99Ms(0L);
            run.setFreshnessAvgMs(0.0);
            return;
        }

        run.setFreshnessP50Ms(percentile(freshnessDeltas, 50));
        run.setFreshnessP95Ms(percentile(freshnessDeltas, 95));
        run.setFreshnessP99Ms(percentile(freshnessDeltas, 99));
        run.setFreshnessAvgMs(freshnessDeltas.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    private void persistArtifacts(BenchmarkRun run) {
        Path reportDirectoryPath = resolveReportDirectory(run);
        run.setReportDirectory(reportDirectoryPath.toString());

        try {
            Files.createDirectories(reportDirectoryPath);
            Files.writeString(reportDirectoryPath.resolve("summary.md"),
                    benchmarkSummary(run), java.nio.charset.StandardCharsets.UTF_8);
            Files.writeString(reportDirectoryPath.resolve("results.json"),
                    benchmarkResultsJson(run), java.nio.charset.StandardCharsets.UTF_8);
            Files.writeString(reportDirectoryPath.resolve("latency.csv"),
                    latencyCsv(run), java.nio.charset.StandardCharsets.UTF_8);
            Files.writeString(reportDirectoryPath.resolve("relevance.csv"),
                    relevanceCsv(run), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist benchmark artifacts", e);
        }

        logger.info("Persisted benchmark artifacts for run {} at {}", run.getId(), reportDirectoryPath);
    }

    private String benchmarkSummary(BenchmarkRun run) {
        return buildReportMarkdown(run);
    }

    private String benchmarkResultsJson(BenchmarkRun run) {
        return buildReportJson(run);
    }

    private String latencyCsv(BenchmarkRun run) {
        return buildLatencyCsv(run);
    }

    private String relevanceCsv(BenchmarkRun run) {
        return buildRelevanceCsv(run);
    }

    private String buildReportMarkdown(BenchmarkRun run) {
        BenchmarkRunSummaryDto summary = toSummaryDto(run);
        List<BenchmarkResultDto> results = toResultDtos(run);

        double avgLatency = results.stream()
                .filter(BenchmarkResultDto::supported)
                .mapToLong(BenchmarkResultDto::latencyMs)
                .average()
                .orElse(0.0);

        StringBuilder builder = new StringBuilder();
        builder.append("# Benchmark Report\n\n");
        builder.append("Run ID: ").append(run.getId()).append("\n");
        builder.append("Query Set: ").append(summary.querySetName()).append("\n");
        builder.append("Status: ").append(summary.status()).append("\n");
        builder.append("Started At: ").append(summary.startedAt()).append("\n");
        builder.append("Completed At: ").append(summary.completedAt()).append("\n");
        builder.append("DurationMs: ").append(summary.durationMs() == null ? 0L : summary.durationMs()).append("\n");
        builder.append("Throughput (queries/sec): ")
                .append(summary.throughputQueriesPerSecond() == null ? 0.0d : String.format("%.2f", summary.throughputQueriesPerSecond()))
                .append("\n");
        builder.append("Latency p50/p95/p99 (ms): ")
                .append(summary.latencyP50Ms()).append(" / ")
                .append(summary.latencyP95Ms()).append(" / ")
                .append(summary.latencyP99Ms()).append("\n");
        builder.append("Freshness p50/p95/p99 (ms): ")
                .append(summary.freshnessP50Ms()).append(" / ")
                .append(summary.freshnessP95Ms()).append(" / ")
                .append(summary.freshnessP99Ms()).append("\n");
        builder.append("Average Latency: ").append(String.format("%.2f ms", avgLatency)).append("\n\n");

        builder.append("| Query | Engine | LatencyMs | ResultCount | Precision@10 | Recall@10 | MRR@10 | nDCG@10 | Error |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");

        for (BenchmarkResultDto result : results) {
            builder.append("| ").append(escape(result.queryText())).append(" |")
                    .append(" ").append(result.engine()).append(" |")
                    .append(" ").append(result.latencyMs()).append(" |")
                    .append(" ").append(result.resultCount()).append(" |")
                    .append(" ").append(formatMetric(result.precisionAtK())).append(" |")
                    .append(" ").append(formatMetric(result.recallAtK())).append(" |")
                    .append(" ").append(formatMetric(result.mrrAtK())).append(" |")
                    .append(" ").append(formatMetric(result.ndcgAtK())).append(" |")
                    .append(" ").append(result.errorMessage() == null ? "" : result.errorMessage().replace("|", "\\|"))
                    .append(" |\n");
        }

        return builder.toString();
    }

    private String buildReportJson(BenchmarkRun run) {
        BenchmarkRunSummaryDto summary = toSummaryDto(run);
        List<BenchmarkResultDto> results = toResultDtos(run);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new BenchmarkReportPayload(summary, results));
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Failed to build benchmark JSON report", e));
        }
    }

    private String buildLatencyCsv(BenchmarkRun run) {
        List<BenchmarkResultDto> results = toResultDtos(run);

        StringBuilder builder = new StringBuilder();
        builder.append("queryText,engine,latencyMs,resultCount,returnedCount,errorMessage\n");
        for (BenchmarkResultDto result : results) {
            builder.append(escapeCsv(result.queryText())).append(',')
                    .append(result.engine()).append(',')
                    .append(result.latencyMs()).append(',')
                    .append(result.resultCount()).append(',')
                    .append(result.returnedCount()).append(',')
                    .append(escapeCsv(result.errorMessage() == null ? "" : result.errorMessage())).append('\n');
        }
        return builder.toString();
    }

    private String buildRelevanceCsv(BenchmarkRun run) {
        List<BenchmarkResultDto> results = toResultDtos(run);

        StringBuilder builder = new StringBuilder();
        builder.append("queryText,engine,precisionAtK,recallAtK,mrrAtK,ndcgAtK\n");
        for (BenchmarkResultDto result : results) {
            builder.append(escapeCsv(result.queryText())).append(',')
                    .append(result.engine()).append(',')
                    .append(formatMetric(result.precisionAtK())).append(',')
                    .append(formatMetric(result.recallAtK())).append(',')
                    .append(formatMetric(result.mrrAtK())).append(',')
                    .append(formatMetric(result.ndcgAtK())).append('\n');
        }
        return builder.toString();
    }

    private BenchmarkRunSummaryDto toSummaryDto(BenchmarkRun run) {
        return new BenchmarkRunSummaryDto(
                run.getId(),
                run.getStatus(),
                run.getQuerySet() == null ? null : run.getQuerySet().getName(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getTotalQueries(),
                run.getTotalEngines(),
                String.format("/api/benchmarks/runs/%d/report.md", run.getId()),
                String.format("/api/benchmarks/runs/%d/report.json", run.getId()),
                String.format("/api/benchmarks/runs/%d/latency.csv", run.getId()),
                String.format("/api/benchmarks/runs/%d/relevance.csv", run.getId()),
                run.getReportDirectory(),
                run.getDurationMs(),
                run.getThroughputQueriesPerSecond(),
                run.getLatencyMinMs(),
                run.getLatencyP50Ms(),
                run.getLatencyP95Ms(),
                run.getLatencyP99Ms(),
                run.getLatencyAvgMs(),
                run.getFreshnessP50Ms(),
                run.getFreshnessP95Ms(),
                run.getFreshnessP99Ms(),
                run.getFreshnessAvgMs()
        );
    }

    private List<BenchmarkResultDto> toResultDtos(BenchmarkRun run) {
        return resultRepository.findByRunOrderByQueryTextAscEngineAsc(run).stream()
                .map(result -> new BenchmarkResultDto(
                        result.getId(),
                        result.getQueryText(),
                        result.getEngine(),
                        result.getLatencyMs() == null ? 0L : result.getLatencyMs(),
                        result.getResultCount() == null ? 0 : result.getResultCount(),
                        result.getReturnedCount() == null ? 0 : result.getReturnedCount(),
                        result.getTopResultProductIds(),
                        result.isSupported(),
                        result.getPrecisionAtK(),
                        result.getRecallAtK(),
                        result.getMrrAtK(),
                        result.getNdcgAtK(),
                        result.getErrorMessage()
                ))
                .toList();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean special = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!special) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String formatMetric(Double value) {
        return value == null ? "" : String.format("%.4f", value);
    }

    private record BenchmarkReportPayload(
            BenchmarkRunSummaryDto run,
            List<BenchmarkResultDto> results
    ) {
    }

    private Path resolveReportDirectory(BenchmarkRun run) {
        String timestamp = run.getStartedAt() == null
                ? LocalDateTime.now().format(TIMESTAMP_FORMAT)
                : run.getStartedAt().format(TIMESTAMP_FORMAT);
        return Paths.get(reportDirectoryBase, timestamp, String.valueOf(run.getId()));
    }

    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        double rank = (percentile / 100.0) * (sortedValues.size() - 1);
        int index = (int) Math.round(rank);
        return sortedValues.get(Math.min(sortedValues.size() - 1, Math.max(0, index)));
    }

    private double computeThroughput(BenchmarkRun run, int queryCount) {
        if (run.getDurationMs() == null || run.getDurationMs() <= 0L || queryCount <= 0) {
            return 0.0;
        }

        int engineCount = SearchEngineType.values().length;
        return (double) queryCount * engineCount * 1000.0 / run.getDurationMs();
    }

    private static long toMs(long nanos) {
        return nanos / 1_000_000;
    }

    private double computeMrr(List<Long> productIds, Map<Long, Integer> relevanceByProduct) {
        for (int idx = 0; idx < productIds.size(); idx++) {
            if (relevanceByProduct.containsKey(productIds.get(idx))) {
                return 1.0d / (idx + 1);
            }
        }
        return 0.0;
    }

    private double computeNdcg(List<Long> productIds, Map<Long, Integer> relevanceByProduct) {
        if (relevanceByProduct.isEmpty()) {
            return 0.0;
        }

        double dcg = 0.0;
        for (int i = 0; i < productIds.size(); i++) {
            Integer relevance = relevanceByProduct.get(productIds.get(i));
            if (relevance == null) {
                continue;
            }
            double logBase = Math.log(i + 2) / Math.log(2);
            dcg += (Math.pow(2, relevance) - 1) / logBase;
        }

        List<Integer> sortedRelevances = relevanceByProduct.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        double idcg = 0.0;
        for (int i = 0; i < sortedRelevances.size(); i++) {
            int relevance = sortedRelevances.get(i);
            double logBase = Math.log(i + 2) / Math.log(2);
            idcg += (Math.pow(2, relevance) - 1) / logBase;
        }

        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }
}
