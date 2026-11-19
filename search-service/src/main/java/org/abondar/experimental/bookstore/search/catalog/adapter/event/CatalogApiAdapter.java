package org.abondar.experimental.bookstore.search.catalog.adapter.event;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.api.CatalogSearchApi;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.model.CatalogSearchResult;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.CatalogSearchQuery;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.SearchCatalogUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CatalogApiAdapter implements CatalogSearchApi {

  private final SearchCatalogUseCase searchCatalogUseCase;

  @Override
  public ResponseEntity<List<CatalogSearchResult>> searchCatalogs(
      @Nullable UUID id, @Nullable String name, Integer limit) {
    if (name != null && name.isBlank()) {
      throw new IllegalArgumentException("Catalog name must not be blank");
    }
    var query = new CatalogSearchQuery(id == null ? null : id.toString(), name, limit);
    return ResponseEntity.ok(toResult(searchCatalogUseCase.searchCatalogs(query)));
  }

  private List<CatalogSearchResult> toResult(List<CatalogDocument> catalogDocuments) {
    return catalogDocuments.stream()
        .map(cd -> new CatalogSearchResult(UUID.fromString(cd.catalogId()), cd.name()))
        .toList();
  }
}
