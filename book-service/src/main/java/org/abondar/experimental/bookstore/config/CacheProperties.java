package org.abondar.experimental.bookstore.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "store.cache")
public record CacheProperties(Duration bookTtl, Duration authorIdTtl) {}
