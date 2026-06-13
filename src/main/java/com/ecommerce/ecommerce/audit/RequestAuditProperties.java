package com.ecommerce.ecommerce.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "app.request-audit")
public class RequestAuditProperties {

    private boolean enabled = true;

    private String path = "/shared-logs/request-audit.log";

    private int maxBodyBytes = 8192;

    private boolean includeHeaders = true;

    private List<String> excludedPathPrefixes = List.of(
            "/actuator",
            "/h2-console"
    );

    private Set<String> sensitiveFields = Set.of(
            "authorization",
            "password",
            "token",
            "secret",
            "apikey",
            "api_key",
            "creditcard",
            "credit_card"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public List<String> getExcludedPathPrefixes() {
        return excludedPathPrefixes;
    }

    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        this.excludedPathPrefixes = excludedPathPrefixes;
    }

    public Set<String> getSensitiveFields() {
        return sensitiveFields;
    }

    public void setSensitiveFields(Set<String> sensitiveFields) {
        this.sensitiveFields = sensitiveFields;
    }
}
