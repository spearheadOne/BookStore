package org.abondar.experimental.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "store.topic")
public record TopicProperties(String bookTopic, String catalogTopic) {}
