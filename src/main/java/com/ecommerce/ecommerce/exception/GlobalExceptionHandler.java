package com.ecommerce.ecommerce.exception;

import com.ecommerce.ecommerce.logging.ApiErrorResponse;
import com.ecommerce.ecommerce.logging.AuditLoggers;
import com.ecommerce.ecommerce.logging.LogCategory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        LogCategory category = AuditLoggers.fromPath(request.getRequestURI());
        ErrorDescriptor error = resolveRuntimeError(ex, category);

        return buildResponse(
                request,
                HttpStatus.BAD_REQUEST,
                category,
                error.errorType(),
                error.code(),
                ex.getMessage(),
                ex,
                false
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .orElse(null);

        String message = fieldError == null
                ? "Validation error"
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();

        return buildResponse(
                request,
                HttpStatus.BAD_REQUEST,
                AuditLoggers.fromPath(request.getRequestURI()),
                "VALIDATION",
                validationCode(fieldError),
                message,
                ex,
                false
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                              HttpServletRequest request) {
        return buildResponse(
                request,
                HttpStatus.FORBIDDEN,
                LogCategory.AUTH,
                "AUTHORIZATION",
                "ACCESS_DENIED",
                ex.getMessage(),
                ex,
                false
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                                HttpServletRequest request) {
        return buildResponse(
                request,
                HttpStatus.UNAUTHORIZED,
                LogCategory.AUTH,
                "AUTHENTICATION",
                "BAD_CREDENTIALS",
                "Invalid username or password",
                ex,
                false
        );
    }

    @ExceptionHandler(InventoryConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleInventoryConflict(InventoryConflictException ex,
                                                                   HttpServletRequest request) {
        return buildResponse(
                request,
                HttpStatus.CONFLICT,
                LogCategory.INVENTORY,
                "CONCURRENCY_CONFLICT",
                inventoryConflictCode(ex),
                ex.getMessage(),
                ex,
                false
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return buildResponse(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                AuditLoggers.fromPath(request.getRequestURI()),
                "INTERNAL_ERROR",
                "UNEXPECTED_ERROR",
                "Unexpected server error. Use the correlationId when reporting this issue.",
                ex,
                true
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpServletRequest request,
                                                          HttpStatus status,
                                                          LogCategory category,
                                                          String errorType,
                                                          String code,
                                                          String message,
                                                          Exception ex,
                                                          boolean includeStackTrace) {
        ApiErrorResponse response = ApiErrorResponse.from(
                request,
                status.value(),
                category,
                errorType,
                code,
                message
        );

        Logger log = AuditLoggers.forCategory(category);
        if (includeStackTrace || status.is5xxServerError()) {
            log.error(
                    "category={} event=api_error errorType={} code={} method={} path={} status={} correlationId={} message={}",
                    category,
                    errorType,
                    code,
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    response.getCorrelationId(),
                    message,
                    ex
            );
        } else {
            log.warn(
                    "category={} event=api_error errorType={} code={} method={} path={} status={} correlationId={} message={} exceptionType={}",
                    category,
                    errorType,
                    code,
                    request.getMethod(),
                    request.getRequestURI(),
                    status.value(),
                    response.getCorrelationId(),
                    message,
                    ex.getClass().getSimpleName()
            );
        }

        return ResponseEntity.status(status).body(response);
    }

    private ErrorDescriptor resolveRuntimeError(RuntimeException ex, LogCategory category) {
        String message = normalize(ex.getMessage());

        if (message.contains("not found")) {
            return new ErrorDescriptor("RESOURCE_NOT_FOUND", notFoundCode(category, message));
        }
        if (message.contains("email already in use")) {
            return new ErrorDescriptor("BUSINESS_RULE", "EMAIL_ALREADY_IN_USE");
        }
        if (message.contains("category already exists")) {
            return new ErrorDescriptor("BUSINESS_RULE", "CATEGORY_ALREADY_EXISTS");
        }
        if (message.contains("insufficient stock")) {
            return new ErrorDescriptor("BUSINESS_RULE", "INSUFFICIENT_STOCK");
        }
        if (message.contains("cart is empty")) {
            return new ErrorDescriptor("BUSINESS_RULE", "CART_EMPTY");
        }
        if (message.contains("no items could be fulfilled") || message.contains("out of stock")) {
            return new ErrorDescriptor("BUSINESS_RULE", "OUT_OF_STOCK");
        }
        if (message.contains("insufficient balance")) {
            return new ErrorDescriptor("BUSINESS_RULE", "INSUFFICIENT_BALANCE");
        }
        if (message.contains("failed to send email")) {
            return new ErrorDescriptor("EXTERNAL_SERVICE_FAILURE", "EMAIL_SEND_FAILED");
        }
        if (message.contains("failed to generate transactions csv")) {
            return new ErrorDescriptor("PROCESSING_ERROR", "TRANSACTIONS_REPORT_GENERATION_FAILED");
        }
        if (message.contains("failed to generate best sellers csv")) {
            return new ErrorDescriptor("PROCESSING_ERROR", "BEST_SELLERS_REPORT_GENERATION_FAILED");
        }
        if (message.contains("injected checkout failure")) {
            return new ErrorDescriptor("TRANSACTION_FAILURE", "CHECKOUT_FAILURE_INJECTED");
        }

        return new ErrorDescriptor("BUSINESS_RULE", category.name() + "_BUSINESS_RULE_VIOLATION");
    }

    private String notFoundCode(LogCategory category, String normalizedMessage) {
        if (normalizedMessage.contains("product")) {
            return "PRODUCT_NOT_FOUND";
        }
        if (normalizedMessage.contains("category")) {
            return "CATEGORY_NOT_FOUND";
        }
        if (normalizedMessage.contains("user")) {
            return "USER_NOT_FOUND";
        }
        if (normalizedMessage.contains("order")) {
            return "ORDER_NOT_FOUND";
        }
        if (normalizedMessage.contains("item")) {
            return "CART_ITEM_NOT_FOUND";
        }

        return category.name() + "_RESOURCE_NOT_FOUND";
    }

    private String validationCode(FieldError fieldError) {
        if (fieldError == null || fieldError.getField() == null || fieldError.getField().isBlank()) {
            return "REQUEST_VALIDATION_FAILED";
        }

        return sanitizeCodePart(fieldError.getField()) + "_INVALID";
    }

    private String inventoryConflictCode(InventoryConflictException ex) {
        String message = normalize(ex.getMessage());

        if (message.contains("busy")) {
            return "INVENTORY_LOCK_BUSY";
        }
        if (message.contains("interrupted")) {
            return "INVENTORY_LOCK_INTERRUPTED";
        }
        if (message.contains("checkout conflicted")) {
            return "CHECKOUT_INVENTORY_CONFLICT";
        }

        return "INVENTORY_CONFLICT";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String sanitizeCodePart(String value) {
        return value.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private record ErrorDescriptor(String errorType, String code) {
    }
}
