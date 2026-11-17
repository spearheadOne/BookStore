package org.abondar.experimental.bookstore.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "book.content")
public record BookContentProperties(int maxExtractedCharacters) {

  public BookContentProperties {
    if (maxExtractedCharacters <= 0) {
      throw new IllegalArgumentException("book.content.max-extracted-characters must be positive");
    }
  }
}
