package com.ecommerce.ecommerce.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;

public class ApiErrorResponse {

    private final String timestamp;
    private final String correlationId;
    private final String domain;
    private final String errorType;
    private final String code;
    private final String message;
    private final String method;
    private final String path;
    private final int status;

    public ApiErrorResponse(String correlationId,
                            LogCategory domain,
                            String errorType,
                            String code,
                            String message,
                            String method,
                            String path,
                            int status) {
        this.timestamp = Instant.now().toString();
        this.correlationId = correlationId;
        this.domain = domain.name();
        this.errorType = errorType;
        this.code = code;
        this.message = message;
        this.method = method;
        this.path = path;
        this.status = status;
    }

    public static ApiErrorResponse from(HttpServletRequest request,
                                        int status,
                                        LogCategory domain,
                                        String errorType,
                                        String code,
                                        String message) {
        return new ApiErrorResponse(
                currentCorrelationId(request),
                domain,
                errorType,
                code,
                message,
                request.getMethod(),
                request.getRequestURI(),
                status
        );
    }

    private static String currentCorrelationId(HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }

        String headerValue = request.getHeader(RequestContextFilter.CORRELATION_ID_HEADER);
        return headerValue == null || headerValue.isBlank() ? "unavailable" : headerValue;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDomain() {
        return domain;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getError() {
        return message;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return status;
    }
}
