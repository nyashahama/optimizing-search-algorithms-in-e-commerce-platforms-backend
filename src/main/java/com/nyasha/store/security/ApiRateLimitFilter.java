package com.nyasha.store.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE_MS = 60_000L;

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, Queue<Long>> requestLogsByClient;

    public ApiRateLimitFilter() {
        this(true, 120, MINUTE_MS, Clock.systemUTC());
    }

    public ApiRateLimitFilter(boolean enabled, int maxRequests, long windowMs, Clock clock) {
        this.enabled = enabled;
        this.maxRequests = maxRequests <= 0 ? 120 : maxRequests;
        this.windowMs = windowMs > 0 ? windowMs : MINUTE_MS;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.requestLogsByClient = new ConcurrentHashMap<>();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || isHealthEndpoint(request) || isExcludedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        long now = clock.millis();

        Queue<Long> requestTimestamps = requestLogsByClient.computeIfAbsent(clientKey, ignored -> new ConcurrentLinkedQueue<>());
        synchronized (requestTimestamps) {
            long windowStart = now - windowMs;
            while (!requestTimestamps.isEmpty() && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll();
            }

            if (requestTimestamps.size() >= maxRequests) {
                long oldest = requestTimestamps.peek() == null ? now : requestTimestamps.peek();
                long resetAtMs = oldest + windowMs;
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("text/plain");
                response.setHeader("Retry-After", String.valueOf(Math.max(1, (resetAtMs - now) / 1000)));
                response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.getWriter().write("Rate limit exceeded");
                return;
            }

            requestTimestamps.add(now);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - requestTimestamps.size())));
        filterChain.doFilter(request, response);
    }

    private boolean isHealthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/actuator/health".equals(path) || "/actuator/info".equals(path);
    }

    private boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/info")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/prometheus")
                || path.startsWith("/actuator/metrics");
    }

    private String resolveClientKey(HttpServletRequest request) {
        String address = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(value -> value.split(",")[0].trim())
                .filter(value -> !value.isBlank())
                .orElse(request.getRemoteAddr());

        return Optional.ofNullable(request.getUserPrincipal())
                .map(principal -> principal.getName() + ":" + address)
                .orElse(address);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/favicon.ico".equals(request.getRequestURI())
                || "/health".equals(request.getRequestURI());
    }
}
