package com.ecommerce.ecommerce.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RequestAuditFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void logsSanitizedJsonRequestBody() throws Exception {
        Path auditLog = tempDir.resolve("request-audit.log");
        RequestAuditFilter filter = new RequestAuditFilter(
                properties(auditLog),
                objectMapper
        );

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/products"
        );
        request.setContentType("application/json");
        request.setContent("""
                {
                  "name": "' OR 1=1--",
                  "description": "<script>alert(1)</script>",
                  "password": "plain-text-password"
                }
                """.getBytes());
        request.addHeader("Authorization", "Bearer real-token");
        request.addHeader("User-Agent", "request-audit-test");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            servletRequest.getInputStream().readAllBytes();
            ((MockHttpServletResponse) servletResponse).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        JsonNode event = objectMapper.readTree(
                Files.readString(auditLog)
        );

        assertThat(event.path("source").asText()).isEqualTo("request-audit");
        assertThat(event.path("method").asText()).isEqualTo("POST");
        assertThat(event.path("path").asText()).isEqualTo("/api/products");
        assertThat(event.path("status").asInt()).isEqualTo(201);
        assertThat(event.path("headers").path("Authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(event.path("body").path("name").asText()).isEqualTo("' OR 1=1--");
        assertThat(event.path("body").path("description").asText()).isEqualTo("<script>alert(1)</script>");
        assertThat(event.path("body").path("password").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void skipsExcludedPaths() throws Exception {
        Path auditLog = tempDir.resolve("request-audit.log");
        RequestAuditFilter filter = new RequestAuditFilter(
                properties(auditLog),
                objectMapper
        );

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/actuator/health"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                }
        );

        assertThat(auditLog).doesNotExist();
    }

    private RequestAuditProperties properties(Path auditLog) {
        RequestAuditProperties properties = new RequestAuditProperties();
        properties.setPath(auditLog.toString());
        properties.setMaxBodyBytes(8192);

        return properties;
    }
}
