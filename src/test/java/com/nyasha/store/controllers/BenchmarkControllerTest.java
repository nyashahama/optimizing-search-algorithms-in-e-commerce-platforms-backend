package com.nyasha.store.controllers;

import com.nyasha.store.benchmark.controllers.BenchmarkController;
import com.nyasha.store.benchmark.dtos.BenchmarkRunRequest;
import com.nyasha.store.benchmark.dtos.BenchmarkRunResponse;
import com.nyasha.store.benchmark.dtos.BenchmarkResultDto;
import com.nyasha.store.benchmark.dtos.BenchmarkRunSummaryDto;
import com.nyasha.store.benchmark.models.BenchmarkRunStatus;
import com.nyasha.store.benchmark.services.BenchmarkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BenchmarkControllerTest {

    @TempDir
    Path tempDir;

    private final BenchmarkService benchmarkService = mock(BenchmarkService.class);
    private final BenchmarkController benchmarkController = new BenchmarkController(benchmarkService);

    @Test
    void benchmarkFlowReturnsExpectedPayloads() {
        BenchmarkRunResponse response = new BenchmarkRunResponse(1L, BenchmarkRunStatus.QUEUED.name(), 4);
        when(benchmarkService.startRun(null, null)).thenReturn(response);

        assertThat(benchmarkController.startRun(null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(benchmarkController.startRun(null).getBody()).isEqualTo(response);
        when(benchmarkService.startRun(5L, 15)).thenReturn(response);
        assertThat(benchmarkController.startRun(new BenchmarkRunRequest(5L, 15)).getBody()).isEqualTo(response);

        BenchmarkRunSummaryDto summary = sampleSummary();
        BenchmarkResultDto result = new BenchmarkResultDto(
                1L, "query", null, 10L, 3, 3,
                "10", true, null, null, null, null, null
        );
        when(benchmarkService.getRun(1L)).thenReturn(summary);
        when(benchmarkService.getResults(1L)).thenReturn(List.of(result));

        assertThat(benchmarkController.getRun(1L).getBody()).isEqualTo(summary);
        assertThat(benchmarkController.getResults(1L).getBody()).hasSize(1);
    }

    @Test
    void reportAndArtifactRoutesMapToServiceOutput() throws IOException {
        when(benchmarkService.buildReportMarkdown(1L)).thenReturn("# report");
        when(benchmarkService.buildReportJson(1L)).thenReturn("{\"status\":\"ok\"}");
        when(benchmarkService.buildLatencyCsv(1L)).thenReturn("latency");
        when(benchmarkService.buildRelevanceCsv(1L)).thenReturn("relevance");

        assertThat(benchmarkController.getReport(1L).getBody()).contains("report");
        assertThat(benchmarkController.getReportJson(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(benchmarkController.getLatencyCsv(1L).getBody()).isEqualTo("latency");
        assertThat(benchmarkController.getRelevanceCsv(1L).getBody()).isEqualTo("relevance");

        Path artifactPath = tempDir.resolve("summary.md");
        Files.writeString(artifactPath, "# artifact");
        when(benchmarkService.getReportPath(any(Long.class), eq("summary.md"))).thenReturn(artifactPath);
        when(benchmarkService.getReportPath(any(Long.class), eq("results.json"))).thenReturn(tempDir.resolve("results.json"));
        when(benchmarkService.getReportPath(any(Long.class), eq("latency.csv"))).thenReturn(tempDir.resolve("latency.csv"));
        when(benchmarkService.getReportPath(any(Long.class), eq("relevance.csv"))).thenReturn(tempDir.resolve("relevance.csv"));
        when(benchmarkService.getReportPath(any(Long.class), eq("missing.csv"))).thenReturn(tempDir.resolve("missing.csv"));

        Files.writeString(tempDir.resolve("results.json"), "{\"ok\":true}");
        Files.writeString(tempDir.resolve("latency.csv"), "lat");
        Files.writeString(tempDir.resolve("relevance.csv"), "rel");

        assertThat(benchmarkController.getArtifact(1L, "summary.md").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(benchmarkController.getArtifact(1L, "summary.md").getBody()).isEqualTo("# artifact");
        assertThat(benchmarkController.getArtifact(1L, "results.json").getBody()).isEqualTo("{\"ok\":true}");
        assertThat(benchmarkController.getArtifact(1L, "latency.csv").getBody()).isEqualTo("lat");
        assertThat(benchmarkController.getArtifact(1L, "relevance.csv").getBody()).isEqualTo("rel");
    }

    @Test
    void unsupportedOrMissingArtifactsReturnExpectedErrors() {
        when(benchmarkService.getReportPath(1L, "summary.md")).thenReturn(tempDir.resolve("summary.md"));
        assertThat(benchmarkController.getArtifact(1L, "missing.csv").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(benchmarkController.getArtifact(1L, "summary.md").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private BenchmarkRunSummaryDto sampleSummary() {
        return new BenchmarkRunSummaryDto(
                1L,
                BenchmarkRunStatus.QUEUED,
                "electronics-basic",
                LocalDateTime.now(),
                null,
                1,
                1,
                "/api/benchmarks/runs/1/report.md",
                "/api/benchmarks/runs/1/report.json",
                "/api/benchmarks/runs/1/latency.csv",
                "/api/benchmarks/runs/1/relevance.csv",
                tempDir.toString(),
                0L,
                0.0,
                0L,
                0L,
                0L,
                0L,
                0.0,
                0L,
                0L,
                0L,
                0.0
        );
    }
}
