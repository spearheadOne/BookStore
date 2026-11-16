package org.abondar.experimental.bookstore.search.catalog.adapter.out;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.abondar.experimental.bookstore.search.catalog.application.port.out.CatalogRepository;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.stereotype.Component;

@Component
public class CatalogOpenSearchAdapter implements CatalogRepository {

  private final OpenSearchProperties properties;
  private final OpenSearchClient client;

  public CatalogOpenSearchAdapter(OpenSearchProperties properties, OpenSearchClient client) {
    this.properties = properties;
    this.client = client;
  }

  @Override
  public void upsertCatalog(String catalogId, String catalogName) {
    try {
      client.index(
          request ->
              request
                  .index(properties.catalogIndex())
                  .id(catalogId)
                  .document(Map.of("catalogId", catalogId, "name", catalogName)));
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not index catalog " + catalogId, exception);
    }
  }

  @Override
  public void deleteCatalog(String catalogId) {
    try {
      client.delete(request -> request.index(properties.catalogIndex()).id(catalogId));
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not delete catalog " + catalogId, exception);
    }
  }
}
