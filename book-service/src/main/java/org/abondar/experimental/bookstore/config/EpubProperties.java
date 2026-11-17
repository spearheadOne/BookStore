package org.abondar.experimental.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "store.epub")
public record EpubProperties(long maxExpandedSizeMb, int maxZipEntries) {

  public EpubProperties {
    if (maxExpandedSizeMb <= 0 || maxZipEntries <= 0) {
      throw new IllegalArgumentException(
          "EPUB expanded size and ZIP entry limits must be positive");
    }
  }
}
