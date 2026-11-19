package org.abondar.experimental.bookstore.search.catalog.application.port.out;

import java.util.List;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.CatalogSearchQuery;

public interface CatalogRepository {

  void upsertCatalog(String catalogId, String catalogName);

  void deleteCatalog(String catalogId);

  List<CatalogDocument> searchCatalogs(CatalogSearchQuery query);
}
