package org.abondar.experimental.bookstore.search.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(
    String url,
    String username,
    String password,
    Duration connectTimeout,
    Duration responseTimeout,
    String catalogIndex,
    String bookMetadataIndex,
    String bookContentIndex,
    int shards,
    int replicas) {
  public List<String> getIndexNames() {
    return List.of(bookMetadataIndex, bookContentIndex, catalogIndex);
  }
}
