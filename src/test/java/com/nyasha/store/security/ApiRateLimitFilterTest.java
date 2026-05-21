package com.nyasha.store.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimitFilterTest {

    @Test
    void rateLimitAppliesAfterConfiguredMaximumRequests() throws ServletException, IOException {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                true,
                1,
                60_000,
                Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);

        response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void excludedHealthEndpointBypassesRateLimiting() throws ServletException, IOException {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                true,
                1,
                60_000,
                Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isNotEqualTo(MediaType.TEXT_PLAIN_VALUE);
    }
}
