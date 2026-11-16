package org.abondar.experimental.bookstore.search.catalog.application.port.in;

public interface UpsertCatalogUseCase {

  void upsertCatalog(String catalogId, String catalogName);
}
