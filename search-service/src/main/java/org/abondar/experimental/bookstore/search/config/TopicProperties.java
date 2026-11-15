package org.abondar.experimental.bookstore.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "topic")
public record TopicProperties(
    String bookTopic,
    String catalogTopic,
    String dltPrefix,
    long backoffInterval,
    long maxAttempts) {}
