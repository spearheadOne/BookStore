package org.abondar.experimental.bookstore.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "s3")
public record S3Properties(
    URI endpoint,
    String bucket,
    String region,
    boolean pathStyle,
    Duration connectTimeout,
    Duration requestTimeout) {}
