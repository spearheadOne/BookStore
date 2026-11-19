package org.abondar.experimental.bookstore.search.catalog.application.port.in;

import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;

public interface UpsertCatalogUseCase {

  void upsertCatalog(CatalogDocument catalogDocument);
}
