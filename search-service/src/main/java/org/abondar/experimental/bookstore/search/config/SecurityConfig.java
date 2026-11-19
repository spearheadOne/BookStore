package org.abondar.experimental.bookstore.search.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.adapter.in.api.ApiRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableMethodSecurity(proxyTargetClass = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final RateLimiterRegistry registry;

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
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
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var converter = new JwtAuthenticationConverter();
    var scopes = new JwtGrantedAuthoritiesConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var authorities = new ArrayList<>(scopes.convert(jwt));
          var resourceAccess = jwt.getClaimAsMap("resource_access");
          if (resourceAccess != null
              && resourceAccess.get("book-search") instanceof Map<?, ?> client
              && client.get("roles") instanceof Collection<?> roles) {
            for (var role : roles) {
              if (role instanceof String name) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
              }
            }
          }
          return authorities;
        });
    return converter;
  }
}
