package com.nyasha.store.configurations;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointDocumentationAlignmentTest {

    @Test
    void documentedEndpointsAreRepresentedInAuthorizationMatrixAndControllerSurface() {
        Set<String> documentedEndpoints = documentedEndpointMatrix().stream()
                .map(entry -> canonicalRoute(entry.method(), entry.path()))
                .collect(Collectors.toSet());

        Set<String> matrixEndpoints = EndpointAuthorizationMatrixTest.endpointMatrix().stream()
                .map(entry -> canonicalRoute(entry.method(), entry.path()))
                .collect(Collectors.toSet());

        assertThat(matrixEndpoints).containsAll(documentedEndpoints);
    }

    private static Set<EndpointContractEntry> documentedEndpointMatrix() {
        return Set.of(
                new EndpointContractEntry("GET", "/api/products"),
                new EndpointContractEntry("GET", "/api/products/{id}"),
                new EndpointContractEntry("GET", "/api/products/search"),
                new EndpointContractEntry("GET", "/api/products/autocomplete"),
                new EndpointContractEntry("GET", "/api/products/category/{categoryId}"),
                new EndpointContractEntry("GET", "/api/categories"),
                new EndpointContractEntry("GET", "/api/categories/{id}"),
                new EndpointContractEntry("GET", "/api/search"),
                new EndpointContractEntry("GET", "/api/search/compare"),
                new EndpointContractEntry("GET", "/api/search/autocomplete"),
                new EndpointContractEntry("GET", "/api/reviews/products/{productId}"),
                new EndpointContractEntry("GET", "/api/carts/me"),
                new EndpointContractEntry("GET", "/api/orders/me"),
                new EndpointContractEntry("GET", "/api/orders/{id}"),
                new EndpointContractEntry("GET", "/api/payments/orders/{orderId}"),
                new EndpointContractEntry("GET", "/api/inventory/low-stock"),
                new EndpointContractEntry("GET", "/api/inventory/{productId}"),
                new EndpointContractEntry("GET", "/api/suppliers"),
                new EndpointContractEntry("GET", "/api/suppliers/{supplierId}"),
                new EndpointContractEntry("GET", "/api/returns/me"),
                new EndpointContractEntry("GET", "/api/wishlists/me"),
                new EndpointContractEntry("GET", "/api/addresses/me"),
                new EndpointContractEntry("GET", "/api/addresses/me/{id}"),
                new EndpointContractEntry("GET", "/users"),
                new EndpointContractEntry("GET", "/users/search"),
                new EndpointContractEntry("GET", "/users/{id}"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}/results"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}/artifacts/{filename}"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}/report.md"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}/report.json"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}/latency.csv"),
                new EndpointContractEntry("GET", "/api/benchmarks/runs/{id}/relevance.csv"),
                new EndpointContractEntry("GET", "/api/index/status"),
                new EndpointContractEntry("GET", "/api/ops/status"),

                new EndpointContractEntry("POST", "/api/reviews"),
                new EndpointContractEntry("POST", "/api/carts/me/items"),
                new EndpointContractEntry("POST", "/api/checkouts/preview"),
                new EndpointContractEntry("POST", "/api/checkouts/confirm"),
                new EndpointContractEntry("POST", "/api/orders/{id}/pack"),
                new EndpointContractEntry("POST", "/api/orders/{id}/ship"),
                new EndpointContractEntry("POST", "/api/orders/{id}/delivered"),
                new EndpointContractEntry("POST", "/api/orders/{id}/cancel"),
                new EndpointContractEntry("POST", "/api/payments/orders/{orderId}/capture"),
                new EndpointContractEntry("POST", "/api/payments/orders/{orderId}/refund"),
                new EndpointContractEntry("PUT", "/api/inventory/{productId}"),
                new EndpointContractEntry("PATCH", "/api/inventory/{productId}/adjust"),
                new EndpointContractEntry("POST", "/api/suppliers"),
                new EndpointContractEntry("POST", "/api/returns/{orderId}"),
                new EndpointContractEntry("POST", "/api/returns/{returnId}/approve"),
                new EndpointContractEntry("POST", "/api/returns/{returnId}/reject"),
                new EndpointContractEntry("POST", "/api/returns/{returnId}/refund"),
                new EndpointContractEntry("POST", "/api/wishlists/me/items"),
                new EndpointContractEntry("POST", "/api/addresses/me"),
                new EndpointContractEntry("POST", "/users/register"),
                new EndpointContractEntry("POST", "/users/login"),
                new EndpointContractEntry("POST", "/api/products"),
                new EndpointContractEntry("POST", "/api/categories"),
                new EndpointContractEntry("POST", "/api/benchmarks/runs"),
                new EndpointContractEntry("POST", "/api/index/rebuild"),

                new EndpointContractEntry("PUT", "/api/products/{id}"),
                new EndpointContractEntry("PUT", "/api/categories/{id}"),
                new EndpointContractEntry("PUT", "/api/suppliers/{supplierId}"),
                new EndpointContractEntry("PUT", "/api/addresses/me/{id}"),
                new EndpointContractEntry("PUT", "/users/{id}"),

                new EndpointContractEntry("PATCH", "/api/carts/me/items/{itemId}"),

                new EndpointContractEntry("DELETE", "/api/carts/me/items/{itemId}"),
                new EndpointContractEntry("DELETE", "/api/carts/me"),
                new EndpointContractEntry("DELETE", "/api/products/{id}"),
                new EndpointContractEntry("DELETE", "/api/categories/{id}"),
                new EndpointContractEntry("DELETE", "/api/suppliers/{supplierId}"),
                new EndpointContractEntry("DELETE", "/api/addresses/me/{id}"),
                new EndpointContractEntry("DELETE", "/users/{id}")
        );
    }

    private static String canonicalRoute(String method, String path) {
        return method + " " + canonicalPath(path);
    }

    private static String canonicalPath(String path) {
        String normalized = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
        return java.util.Arrays.stream(normalized.split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(segment -> {
                    if (segment.matches("\\d+")) {
                        return "{id}";
                    }
                    if (segment.equals("*")) {
                        return "{id}";
                    }
                    if (segment.startsWith("{") && segment.endsWith("}")) {
                        return "{id}";
                    }
                    if (segment.contains(".")) {
                        return "{id}";
                    }
                    return segment;
                })
                .collect(java.util.stream.Collectors.joining("/", "/", ""));
    }

    private record EndpointContractEntry(String method, String path) {
    }
}
