package com.nyasha.store.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenSearchSearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String indexName;
    private volatile Boolean initialized;

    public OpenSearchSearchClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${search.infrastructure.opensearch.base-url:http://localhost:9200}") String baseUrl,
            @Value("${search.infrastructure.opensearch.index-name:products}") String indexName
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.indexName = indexName;
    }

    public void indexDocument(ProductSearchDocument document) {
        ensureIndex();
        try {
            restTemplate.put(
                    URI.create(url(String.format("/%s/_doc/%s", indexName, document.productId()))),
                    document
            );
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Failed to index product document", e);
        }
    }

    public void deleteDocument(Long productId) {
        ensureIndex();
        try {
            restTemplate.delete(URI.create(url(String.format("/%s/_doc/%s", indexName, productId))));
        } catch (HttpClientErrorException.NotFound ignored) {
            // deletion is idempotent
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Failed to delete product document", e);
        }
    }

    public List<Long> searchProductIds(String query, int limit) {
        ensureIndex();
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "size", limit,
                    "query", Map.of(
                            "multi_match", Map.of(
                                    "query", query,
                                    "fields", List.of("name^3", "description", "sku", "brand^2", "attributes", "categories")
                            )
                    )
            ));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    URI.create(url(String.format("/%s/_search", indexName))),
                    new HttpEntity<>(payload, jsonHeaders()),
                    String.class
            );

            JsonNode body = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            JsonNode hits = body.path("hits").path("hits");

            if (!hits.isArray()) {
                return List.of();
            }

            List<Long> productIds = new ArrayList<>();
            hits.forEach(hit -> {
                JsonNode productId = hit.path("_source").path("productId");
                if (!productId.isMissingNode() && productId.canConvertToLong()) {
                    productIds.add(productId.asLong());
                }
            });

            return productIds;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Failed to query OpenSearch", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenSearch response", e);
        }
    }

    public List<String> suggest(String prefix, int limit) {
        ensureIndex();
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "size", limit,
                    "query", Map.of(
                            "prefix", Map.of(
                                    "name", Map.of(
                                            "value", prefix.toLowerCase()
                                    )
                            )
                    ),
                    "_source", List.of("name")
            ));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    URI.create(url(String.format("/%s/_search", indexName))),
                    new HttpEntity<>(payload, jsonHeaders()),
                    String.class
            );

            JsonNode body = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            JsonNode hits = body.path("hits").path("hits");
            if (!hits.isArray()) {
                return List.of();
            }

            List<String> suggestions = new ArrayList<>();
            hits.forEach(hit -> {
                JsonNode name = hit.path("_source").path("name");
                if (!name.isMissingNode() && !name.asText().isBlank()) {
                    suggestions.add(name.asText());
                }
            });

            return suggestions;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Failed to query autocomplete in OpenSearch", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenSearch autocomplete response", e);
        }
    }

    public long countDocuments() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    URI.create(url(String.format("/%s/_count", indexName))),
                    String.class
            );

            JsonNode body = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            return body.path("count").asLong(0L);
        } catch (RestClientResponseException e) {
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void ensureIndex() {
        if (Boolean.TRUE.equals(initialized)) {
            return;
        }

        synchronized (this) {
            if (Boolean.TRUE.equals(initialized)) {
                return;
            }
            createIndexIfMissing();
            initialized = true;
        }
    }

    private void createIndexIfMissing() {
        try {
            String checkUrl = url(String.format("/%s", indexName));
            restTemplate.headForHeaders(checkUrl);
            return;
        } catch (HttpClientErrorException.NotFound ignored) {
            // proceed to create index when not found
        } catch (RestClientResponseException ignored) {
            // leave initialized and fail operations quickly later
            return;
        }

        try {
            String settings = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"properties\": {\n" +
                    "      \"productId\": {\"type\": \"long\"},\n" +
                    "      \"name\": {\"type\": \"text\"},\n" +
                    "      \"description\": {\"type\": \"text\"},\n" +
                    "      \"sku\": {\"type\": \"keyword\"},\n" +
                    "      \"brand\": {\"type\": \"keyword\"},\n" +
                    "      \"attributes\": {\"type\": \"text\"},\n" +
                    "      \"inventoryStatus\": {\"type\": \"keyword\"},\n" +
                    "      \"categories\": {\"type\": \"keyword\"}\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n";
            restTemplate.put(url(String.format("/%s", indexName)), settings, String.class);
        } catch (RestClientResponseException ignored) {
            // best effort: if create fails, we'll retry on next operation
        }
    }

    private String url(String path) {
        return baseUrl + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
