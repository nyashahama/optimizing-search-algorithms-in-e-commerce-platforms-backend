package com.nyasha.store.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            if (request.getUserPrincipal() != null) {
                MDC.put("authPrincipal", request.getUserPrincipal().getName());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
            MDC.remove("authPrincipal");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/favicon.ico".equals(request.getRequestURI()) || "/health".equals(request.getRequestURI());
    }
}
