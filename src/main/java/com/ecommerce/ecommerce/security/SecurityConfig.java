package com.ecommerce.ecommerce.security;


import com.ecommerce.ecommerce.capacity.RequestCapacityFilter;
import com.ecommerce.ecommerce.capacity.RequestCapacityGuard;
import com.ecommerce.ecommerce.logging.ApiErrorResponse;
import com.ecommerce.ecommerce.logging.AuditLoggers;
import com.ecommerce.ecommerce.logging.LogCategory;
import com.ecommerce.ecommerce.logging.RequestContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger authLog = AuditLoggers.forCategory(LogCategory.AUTH);

    private final JwtAuthFilter jwtAuthFilter;
    private final RequestCapacityGuard requestCapacityGuard;
    private final long requestCapacityWaitMillis;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            RequestCapacityGuard requestCapacityGuard,
            @Value("${app.capacity.max-wait-ms:200}") long requestCapacityWaitMillis,
            ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.requestCapacityGuard = requestCapacityGuard;
        this.requestCapacityWaitMillis = requestCapacityWaitMillis;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RequestCapacityFilter requestCapacityFilter =
                new RequestCapacityFilter(requestCapacityGuard, requestCapacityWaitMillis, objectMapper);

        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/auth/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/metrics/**",
                        "/actuator/prometheus",
                        "/h2-console/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new RequestContextFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(requestCapacityFilter, RequestContextFilter.class)
            .addFilterAfter(jwtAuthFilter, RequestCapacityFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            ApiErrorResponse errorResponse = ApiErrorResponse.from(
                    request,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    LogCategory.AUTH,
                    "AUTHENTICATION",
                    "AUTHENTICATION_REQUIRED",
                    "Authentication is required to access this endpoint."
            );

            authLog.warn(
                    "category=AUTH event=api_error errorType=AUTHENTICATION code=AUTHENTICATION_REQUIRED method={} path={} status={} correlationId={} message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    HttpServletResponse.SC_UNAUTHORIZED,
                    errorResponse.getCorrelationId(),
                    errorResponse.getMessage()
            );

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), errorResponse);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
