package com.ecommerce.ecommerce.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class RequestAuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestAuditFilter.class);

    private static final String MASK = "[REDACTED]";

    private final RequestAuditProperties properties;

    private final ObjectMapper objectMapper;

    private final Object writeLock = new Object();

    public RequestAuditFilter(RequestAuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();

        return properties.getExcludedPathPrefixes()
                .stream()
                .anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(
                request,
                properties.getMaxBodyBytes()
        );

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            writeAuditLine(wrappedRequest, response);
        }
    }

    private void writeAuditLine(ContentCachingRequestWrapper request, HttpServletResponse response) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();

            record.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            record.put("source", "request-audit");
            record.put("method", request.getMethod());
            record.put("path", request.getRequestURI());
            record.put("query", request.getQueryString());
            record.put("status", response.getStatus());
            record.put("client_ip", getClientIp(request));
            record.put("content_type", request.getContentType());
            record.put("content_length", request.getContentLengthLong());
            record.put("body_truncated", isBodyTruncated(request));

            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                record.put("user_agent", userAgent);
            }

            if (properties.isIncludeHeaders()) {
                record.put("headers", sanitizeHeaders(request));
            }

            Object body = sanitizeBody(request);
            if (body != null) {
                record.put("body", body);
            }

            appendLine(
                    objectMapper.writeValueAsString(record)
            );
        } catch (Exception ex) {
            log.warn("Failed to write request audit log", ex);
        }
    }

    private Map<String, String> sanitizeHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);

            if (isSensitive(name)) {
                headers.put(name, MASK);
            } else {
                headers.put(name, value);
            }
        }

        return headers;
    }

    private Object sanitizeBody(ContentCachingRequestWrapper request) throws JsonProcessingException {
        byte[] body = request.getContentAsByteArray();

        if (body.length == 0 || !isTextBody(request.getContentType())) {
            return null;
        }

        String bodyText = new String(
                body,
                getCharset(request.getCharacterEncoding())
        );

        if (isJson(request.getContentType())) {
            try {
                JsonNode json = objectMapper.readTree(bodyText);

                return sanitizeJson(json);
            } catch (JsonProcessingException ex) {
                return sanitizePlainText(bodyText);
            }
        }

        return sanitizePlainText(bodyText);
    }

    private JsonNode sanitizeJson(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();

            node.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    sanitized.set(entry.getKey(), TextNode.valueOf(MASK));
                } else {
                    sanitized.set(entry.getKey(), sanitizeJson(entry.getValue()));
                }
            });

            return sanitized;
        }

        if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();

            node.forEach(item -> sanitized.add(sanitizeJson(item)));

            return sanitized;
        }

        return node;
    }

    private String sanitizePlainText(String bodyText) {
        String sanitized = bodyText;

        for (String field : properties.getSensitiveFields()) {
            sanitized = sanitized.replaceAll(
                    "(?i)(" + Pattern.quote(field) + "\\s*[=:]\\s*)[^&\\s]+",
                    "$1" + MASK
            );
        }

        return sanitized;
    }

    private void appendLine(String line) throws IOException {
        Path path = Path.of(properties.getPath());
        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        synchronized (writeLock) {
            Files.writeString(
                    path,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        }
    }

    private boolean isTextBody(String contentType) {
        if (contentType == null) {
            return false;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);

        return normalized.startsWith(MediaType.APPLICATION_JSON_VALUE)
                || normalized.startsWith(MediaType.TEXT_PLAIN_VALUE)
                || normalized.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || normalized.contains("+json");
    }

    private boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);

        return normalized.startsWith(MediaType.APPLICATION_JSON_VALUE)
                || normalized.contains("+json");
    }

    private boolean isBodyTruncated(ContentCachingRequestWrapper request) {
        long contentLength = request.getContentLengthLong();

        return contentLength > properties.getMaxBodyBytes()
                || request.getContentAsByteArray().length >= properties.getMaxBodyBytes();
    }

    private Charset getCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private boolean isSensitive(String name) {
        String normalized = name
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);

        Set<String> sensitiveFields = properties.getSensitiveFields();

        return sensitiveFields.stream()
                .map(value -> value
                        .replace("-", "")
                        .replace("_", "")
                        .toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }
}
