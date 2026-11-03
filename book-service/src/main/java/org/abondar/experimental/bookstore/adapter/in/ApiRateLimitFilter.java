package org.abondar.experimental.bookstore.adapter.in;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {

  private final RateLimiter reads;
  private final RateLimiter writes;

  public ApiRateLimitFilter(RateLimiterRegistry registry) {
    reads = registry.rateLimiter("reads");
    writes = registry.rateLimiter("writes");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    return !request.getServletPath().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var limiter =
        switch (request.getMethod()) {
          case "GET", "HEAD" -> reads;
          default -> writes;
        };

    if (!limiter.acquirePermission()) {
      response.setStatus(429);
      return;
    }

    filterChain.doFilter(request, response);
  }
}
