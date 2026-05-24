package com.nyasha.store.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenSearchSearchClientTest {

    @Test
    void searchCreatesMissingIndexWithJsonContentTypeBeforeQuerying() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OpenSearchSearchClient client = new OpenSearchSearchClient(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:9200",
                "products"
        );

        server.expect(once(), requestTo("http://localhost:9200/products"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("http://localhost:9200/products"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"productId\"")))
                .andRespond(withSuccess("{\"acknowledged\":true}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://localhost:9200/products/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        List<Long> productIds = client.searchProductIds("wireless", 3);

        assertThat(productIds).isEmpty();
        server.verify();
    }

    @Test
    void returnsHealthyWhenClusterReportsGreenOrYellow() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OpenSearchSearchClient client = new OpenSearchSearchClient(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:9200",
                "products"
        );

        server.expect(times(2), requestTo("http://localhost:9200/_cluster/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"green\"}", MediaType.APPLICATION_JSON));

        assertThat(client.clusterHealthStatus()).isEqualTo("green");
        assertThat(client.isHealthy()).isTrue();
        server.verify();
    }

    @Test
    void returnsUnknownWhenClusterIsUnavailable() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        OpenSearchSearchClient client = new OpenSearchSearchClient(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:9200",
                "products"
        );

        server.expect(times(2), requestTo("http://localhost:9200/_cluster/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.clusterHealthStatus()).isEqualTo("unreachable");
        assertThat(client.isHealthy()).isFalse();
        server.verify();
    }
}
