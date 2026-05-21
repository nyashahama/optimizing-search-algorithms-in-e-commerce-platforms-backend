package com.nyasha.store.benchmark.controllers;

import com.nyasha.store.benchmark.dtos.BenchmarkRunRequest;
import com.nyasha.store.benchmark.dtos.BenchmarkRunResponse;
import com.nyasha.store.benchmark.dtos.BenchmarkRunSummaryDto;
import com.nyasha.store.benchmark.services.BenchmarkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping("/runs")
    public ResponseEntity<BenchmarkRunResponse> startRun(
            @RequestBody(required = false) BenchmarkRunRequest request
    ) {
        Long querySetId = request == null ? null : request.querySetId();
        Integer limit = request == null ? null : request.limit();
        return ResponseEntity.ok(benchmarkService.startRun(querySetId, limit));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<BenchmarkRunSummaryDto> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(benchmarkService.getRun(runId));
    }

    @GetMapping("/runs/{runId}/results")
    public ResponseEntity<java.util.List<com.nyasha.store.benchmark.dtos.BenchmarkResultDto>> getResults(@PathVariable Long runId) {
        return ResponseEntity.ok(benchmarkService.getResults(runId));
    }

    @GetMapping(value = "/runs/{runId}/report.md", produces = "text/markdown")
    public ResponseEntity<String> getReport(@PathVariable Long runId) {
        String report = benchmarkService.buildReportMarkdown(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=report.md")
                .contentType(MediaType.parseMediaType("text/markdown"))
                .contentLength(report.getBytes(StandardCharsets.UTF_8).length)
                .body(report);
    }

    @GetMapping(value = "/runs/{runId}/report.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getReportJson(@PathVariable Long runId) {
        String report = benchmarkService.buildReportJson(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=results.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(report);
    }

    @GetMapping(value = "/runs/{runId}/latency.csv", produces = "text/csv")
    public ResponseEntity<String> getLatencyCsv(@PathVariable Long runId) {
        String csv = benchmarkService.buildLatencyCsv(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=latency.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping(value = "/runs/{runId}/relevance.csv", produces = "text/csv")
    public ResponseEntity<String> getRelevanceCsv(@PathVariable Long runId) {
        String csv = benchmarkService.buildRelevanceCsv(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=relevance.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping(value = "/runs/{runId}/artifacts/{filename}")
    public ResponseEntity<String> getArtifact(@PathVariable Long runId, @PathVariable String filename) {
        return switch (filename) {
            case "summary.md", "results.json", "latency.csv", "relevance.csv" -> {
                Path reportPath = benchmarkService.getReportPath(runId, filename);
                if (!Files.exists(reportPath)) {
                    yield ResponseEntity.status(HttpStatus.NOT_FOUND).body("Artifact not found");
                }

                String body;
                try {
                    body = Files.readString(reportPath);
                } catch (IOException e) {
                    yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to read artifact: " + e.getMessage());
                }

                MediaType contentType = switch (filename) {
                    case "summary.md" -> MediaType.parseMediaType("text/markdown");
                    case "results.json" -> MediaType.APPLICATION_JSON;
                    default -> MediaType.parseMediaType("text/csv");
                };
                yield ResponseEntity.ok()
                        .contentType(contentType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
                        .body(body);
            }
            default -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported artifact");
        };
    }
}
