package com.ecommerce.ecommerce.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RichLoggingAspect {

    @Around("@annotation(richLog)")
    public Object logBusinessOperation(ProceedingJoinPoint joinPoint, RichLog richLog) throws Throwable {
        Logger log = AuditLoggers.forCategory(richLog.category());
        long startedAt = System.currentTimeMillis();
        String method = joinPoint.getSignature().toShortString();

        try {
            Object result = joinPoint.proceed();
            log.info(
                    "category={} event=operation_completed action={} service={} httpMethod={} path={} status=SUCCESS durationMs={} correlationId={}",
                    richLog.category(),
                    richLog.action(),
                    method,
                    mdcValue("method"),
                    mdcValue("path"),
                    System.currentTimeMillis() - startedAt,
                    mdcValue("correlationId")
            );
            return result;
        } catch (Throwable ex) {
            if (MDC.get("path") == null) {
                log.warn(
                        "category={} event=operation_failed action={} service={} status=FAILED durationMs={} errorType={} message={}",
                        richLog.category(),
                        richLog.action(),
                        method,
                        System.currentTimeMillis() - startedAt,
                        ex.getClass().getSimpleName(),
                        ex.getMessage()
                );
            }
            throw ex;
        }
    }

    private String mdcValue(String key) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? "n/a" : value;
    }
}
