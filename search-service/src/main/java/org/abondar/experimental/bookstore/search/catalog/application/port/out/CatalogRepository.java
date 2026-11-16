package org.abondar.experimental.bookstore.search.catalog.application.port.out;

public interface CatalogRepository {

  void upsertCatalog(String catalogId, String catalogName);

  void deleteCatalog(String catalogId);
}
