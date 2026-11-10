package org.abondar.experimental.bookstore.catalog.application.port.out;

import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

public interface CatalogEventRepository {

  void sendCatalogUpserted(Catalog catalog);

  void sendCatalogDeleted(Id<Catalog> catalogId);
}
