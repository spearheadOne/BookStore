package org.abondar.experimental.bookstore.search.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.abondar.experimental.infrastructure.security.ApiRateLimitFilter;
import org.abondar.experimental.infrastructure.security.ResourceServerSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
@Import(ResourceServerSecurityConfig.class)
@EnableMethodSecurity(proxyTargetClass = true)
public class SecurityConfig {
  @Bean
  ApiRateLimitFilter apiRateLimitFilter(RateLimiterRegistry registry) {
    return ApiRateLimitFilter.apiV1(registry);
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
