package com.ecommerce.ecommerce.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class AuditLoggers {

    private static final String BASE_LOGGER = "com.ecommerce.audit.";

    private AuditLoggers() {
    }

    public static Logger forCategory(LogCategory category) {
        return LoggerFactory.getLogger(BASE_LOGGER + category.name().toLowerCase(Locale.ROOT));
    }

    public static LogCategory fromPath(String path) {
        if (path == null) {
            return LogCategory.SYSTEM;
        }

        if (path.startsWith("/api/auth")) {
            return LogCategory.AUTH;
        }
        if (path.startsWith("/api/cart")) {
            return LogCategory.CART;
        }
        if (path.startsWith("/api/orders")) {
            return LogCategory.ORDER;
        }
        if (path.startsWith("/api/products")) {
            return LogCategory.PRODUCT;
        }
        if (path.startsWith("/api/categories")) {
            return LogCategory.CATEGORY;
        }
        if (path.startsWith("/api/transactions")) {
            return LogCategory.TRANSACTION;
        }
        if (path.startsWith("/api/users")) {
            return LogCategory.USER;
        }
        if (path.startsWith("/api/reports")) {
            return LogCategory.BATCH;
        }

        return LogCategory.SYSTEM;
    }
}
