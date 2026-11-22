package org.abondar.experimental.bookstore.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.abondar.experimental.infrastructure.security.ApiRateLimitFilter;
import org.abondar.experimental.infrastructure.security.ResourceServerSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration(proxyBeanMethods = false)
@Import(ResourceServerSecurityConfig.class)
public class SecurityConfig {
  @Bean
  ApiRateLimitFilter apiRateLimitFilter(RateLimiterRegistry registry) {
    return ApiRateLimitFilter.apiV1(registry);
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    return new JwtAuthenticationConverter();
  }
}
