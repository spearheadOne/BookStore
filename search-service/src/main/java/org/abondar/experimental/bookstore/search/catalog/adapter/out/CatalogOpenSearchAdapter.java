package org.abondar.experimental.bookstore.search.catalog.adapter.out;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogEventException;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogSearchException;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.CatalogSearchQuery;
import org.abondar.experimental.bookstore.search.catalog.application.port.out.CatalogRepository;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogOpenSearchAdapter implements CatalogRepository {

  private final OpenSearchProperties properties;
  private final OpenSearchClient client;

  @Override
  public void upsertCatalog(String catalogId, String catalogName) {
    try {
      client.index(
          request ->
              request
                  .index(properties.catalogIndex())
                  .id(catalogId)
                  .document(Map.of("catalogId", catalogId, "name", catalogName)));
    } catch (IOException | OpenSearchException exception) {
      throw new CatalogEventException("Could not index catalog " + catalogId, exception);
    }
  }

  @Override
  public void deleteCatalog(String catalogId) {
    try {
      client.delete(request -> request.index(properties.catalogIndex()).id(catalogId));
    } catch (IOException | OpenSearchException exception) {
      throw new CatalogEventException("Could not delete catalog " + catalogId, exception);
    }
  }

  @Override
  public List<CatalogDocument> searchCatalogs(CatalogSearchQuery query) {
    try {
      var boolQuery = new BoolQuery.Builder();
      if (query.catalogId() != null) {
        boolQuery.filter(
            q -> q.term(t -> t.field("catalogId").value(FieldValue.of(query.catalogId()))));
      }

      if (query.name() != null) {
        boolQuery.must(q -> q.match(m -> m.field("name").query(FieldValue.of(query.name()))));
      }

      var catalogQuery = Query.of(q -> q.bool(boolQuery.build()));

      var response =
          client.search(
              request ->
                  request.index(properties.catalogIndex()).query(catalogQuery).size(query.limit()),
              CatalogDocument.class);

      return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();

    } catch (IOException | OpenSearchException ex) {
      throw new CatalogSearchException(
          "Error finding catalogs in index: " + properties.catalogIndex(), ex);
    }
  }
}
