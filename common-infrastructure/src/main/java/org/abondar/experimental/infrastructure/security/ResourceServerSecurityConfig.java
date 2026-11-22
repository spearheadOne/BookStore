package org.abondar.experimental.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration(proxyBeanMethods = false)
public class ResourceServerSecurityConfig {
  private static final String[] COMMON_PUBLIC_PATHS = {
    "/actuator/health",
    "/actuator/health/**",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/openapi.yaml"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ApiRateLimitFilter rateLimitFilter,
      @Qualifier("servicePublicPaths") ObjectProvider<String[]> servicePublicPaths,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    var additionalPublicPaths = servicePublicPaths.getIfAvailable(() -> new String[0]);
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterAfter(rateLimitFilter, AuthorizationFilter.class)
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(COMMON_PUBLIC_PATHS).permitAll();
              if (additionalPublicPaths.length > 0) {
                auth.requestMatchers(additionalPublicPaths).permitAll();
              }
              auth.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }

  @Bean
  FilterRegistrationBean<ApiRateLimitFilter> rateLimitFilterRegistration(
      ApiRateLimitFilter filter) {
    // Run only inside the security chain, after authentication and authorization.
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
