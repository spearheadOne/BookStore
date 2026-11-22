package org.abondar.experimental.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {
  @Test
  void servicesCanChoosePathsMethodsAndNamedLimits() throws Exception {
    var config =
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofHours(1))
            .timeoutDuration(Duration.ZERO)
            .build();
    var registry = RateLimiterRegistry.of(config);
    var filter =
        new ApiRateLimitFilter(
            registry,
            request -> request.getServletPath().startsWith("/custom/"),
            request -> request.getMethod().equals("POST") ? "customWrites" : "customReads");
    var request = new MockHttpServletRequest("GET", "/custom/books");
    request.setServletPath("/custom/books");
    var response = new MockHttpServletResponse();
    var chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);

    var blockedChain = mock(FilterChain.class);
    filter.doFilter(request, response, blockedChain);
    assertEquals(429, response.getStatus());
    verifyNoInteractions(blockedChain);

    request.setMethod("POST");
    response = new MockHttpServletResponse();
    var writeChain = mock(FilterChain.class);
    filter.doFilter(request, response, writeChain);
    verify(writeChain).doFilter(request, response);

    request.setMethod("GET");
    request.setServletPath("/public");
    response = new MockHttpServletResponse();
    var publicChain = mock(FilterChain.class);
    filter.doFilter(request, response, publicChain);
    verify(publicChain).doFilter(request, response);
  }
}
