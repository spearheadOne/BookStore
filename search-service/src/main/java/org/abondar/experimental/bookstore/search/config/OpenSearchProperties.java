package org.abondar.experimental.bookstore.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(
    String url, String username, String password, String connectTimeout, String responseTimeout) {}
