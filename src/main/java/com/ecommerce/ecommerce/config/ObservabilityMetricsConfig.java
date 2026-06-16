package com.ecommerce.ecommerce.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.function.ToIntFunction;

@Configuration
public class ObservabilityMetricsConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonMetricTags(
            @Value("${spring.application.name:ecommerce}") String applicationName) {
        return registry -> registry.config().commonTags("application", applicationName);
    }

    @Bean
    ApplicationRunner bindRuntimeMetrics(
            MeterRegistry meterRegistry,
            DataSource dataSource) {
        return args -> {
            bindJvmMetrics(meterRegistry);
            bindHikariMetrics(meterRegistry, dataSource);
        };
    }

    private void bindJvmMetrics(MeterRegistry meterRegistry) {
        if (meterRegistry.find("jvm.memory.used").meters().isEmpty()) {
            new JvmMemoryMetrics().bindTo(meterRegistry);
        }
        if (meterRegistry.find("jvm.threads.live").meters().isEmpty()) {
            new JvmThreadMetrics().bindTo(meterRegistry);
        }
        if (meterRegistry.find("jvm.gc.memory.promoted").meters().isEmpty()) {
            new JvmGcMetrics().bindTo(meterRegistry);
        }
        if (meterRegistry.find("jvm.classes.loaded").meters().isEmpty()) {
            new ClassLoaderMetrics().bindTo(meterRegistry);
        }
    }

    private void bindHikariMetrics(MeterRegistry meterRegistry, DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)
                || !meterRegistry.find("hikaricp.connections.active").meters().isEmpty()) {
            return;
        }

        Tags tags = Tags.of("pool", hikariDataSource.getPoolName());
        Gauge.builder("hikaricp.connections.active", hikariDataSource,
                        source -> poolValue(source, HikariPoolMXBean::getActiveConnections))
                .description("Active HikariCP database connections")
                .tags(tags)
                .register(meterRegistry);

        Gauge.builder("hikaricp.connections.idle", hikariDataSource,
                        source -> poolValue(source, HikariPoolMXBean::getIdleConnections))
                .description("Idle HikariCP database connections")
                .tags(tags)
                .register(meterRegistry);

        Gauge.builder("hikaricp.connections.pending", hikariDataSource,
                        source -> poolValue(source, HikariPoolMXBean::getThreadsAwaitingConnection))
                .description("Threads waiting for a HikariCP database connection")
                .tags(tags)
                .register(meterRegistry);

        Gauge.builder("hikaricp.connections", hikariDataSource,
                        source -> poolValue(source, HikariPoolMXBean::getTotalConnections))
                .description("Total HikariCP database connections")
                .tags(tags.and("state", "total"))
                .register(meterRegistry);
    }

    private double poolValue(HikariDataSource dataSource, ToIntFunction<HikariPoolMXBean> valueExtractor) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        return pool == null ? 0 : valueExtractor.applyAsInt(pool);
    }
}
