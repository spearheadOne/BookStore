package org.abondar.experimental.bookstore.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.ApiRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final RateLimiterRegistry registry;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterAfter(new ApiRateLimitFilter(registry), AuthorizationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/openapi.yaml")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
        .build();
  }
}
