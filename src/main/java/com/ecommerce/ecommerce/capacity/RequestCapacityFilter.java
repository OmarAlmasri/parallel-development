package com.ecommerce.ecommerce.capacity;

import com.ecommerce.ecommerce.logging.ApiErrorResponse;
import com.ecommerce.ecommerce.logging.AuditLoggers;
import com.ecommerce.ecommerce.logging.LogCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestCapacityFilter extends OncePerRequestFilter {

    private static final Logger log = AuditLoggers.forCategory(LogCategory.CAPACITY);

    private final RequestCapacityGuard requestCapacityGuard;
    private final long maxWaitMillis;
    private final ObjectMapper objectMapper;

    public RequestCapacityFilter(RequestCapacityGuard requestCapacityGuard, long maxWaitMillis) {
        this(requestCapacityGuard, maxWaitMillis, new ObjectMapper());
    }

    public RequestCapacityFilter(RequestCapacityGuard requestCapacityGuard,
                                 long maxWaitMillis,
                                 ObjectMapper objectMapper) {
        this.requestCapacityGuard = requestCapacityGuard;
        this.maxWaitMillis = maxWaitMillis;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/")
                || path.startsWith("/api/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/h2-console/")
                || "/error".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        boolean acquired;
        try {
            acquired = requestCapacityGuard.tryAcquire(maxWaitMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writeCapacityError(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CAPACITY_INTERRUPTED",
                    "Request handling was interrupted."
            );
            return;
        }

        if (!acquired) {
            response.setHeader("Retry-After", "1");
            writeCapacityError(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "CAPACITY_LIMIT_REACHED",
                    "Server is at request capacity. Please retry."
            );
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            requestCapacityGuard.release();
        }
    }

    private void writeCapacityError(HttpServletRequest request,
                                    HttpServletResponse response,
                                    HttpStatus status,
                                    String code,
                                    String message) throws IOException {
        ApiErrorResponse errorResponse = ApiErrorResponse.from(
                request,
                status.value(),
                LogCategory.CAPACITY,
                "CAPACITY_LIMIT",
                code,
                message
        );

        log.warn(
                "category=CAPACITY event=api_error errorType=CAPACITY_LIMIT code={} method={} path={} status={} correlationId={} message={}",
                code,
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                errorResponse.getCorrelationId(),
                message
        );

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
