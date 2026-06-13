package com.ecommerce.ecommerce.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(RequestAuditProperties.class)
public class RequestAuditConfig {

    @Bean
    public FilterRegistrationBean<RequestAuditFilter> requestAuditFilter(
            RequestAuditProperties properties,
            ObjectMapper objectMapper) {

        FilterRegistrationBean<RequestAuditFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new RequestAuditFilter(properties, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");

        return registration;
    }
}
