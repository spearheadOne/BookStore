package org.abondar.experimental.infrastructure.security;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {

  private final RateLimiterRegistry registry;
  private final Predicate<HttpServletRequest> appliesTo;
  private final Function<HttpServletRequest, String> limiterName;

  public ApiRateLimitFilter(
      RateLimiterRegistry registry,
      Predicate<HttpServletRequest> appliesTo,
      Function<HttpServletRequest, String> limiterName) {
    this.registry = registry;
    this.appliesTo = appliesTo;
    this.limiterName = limiterName;
  }

  /**
   * Default API policy; services can supply different predicates and limiter names via the
   * constructor.
   */
  public static ApiRateLimitFilter apiV1(RateLimiterRegistry registry) {
    return new ApiRateLimitFilter(
        registry,
        request -> request.getServletPath().startsWith("/api/v1/"),
        request ->
            switch (request.getMethod()) {
              case "GET", "HEAD" -> "reads";
              default -> "writes";
            });
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    return !appliesTo.test(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var limiter = registry.rateLimiter(limiterName.apply(request));

    if (!limiter.acquirePermission()) {
      response.setStatus(429);
      return;
    }

    filterChain.doFilter(request, response);
  }
}
