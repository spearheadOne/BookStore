package org.abondar.experimental.bookstore.catalog.application.port.in;

import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

public interface UpdateCatalogNameUseCase {
  Catalog updateCatalogName(Id<Catalog> catalogId, String name);
}
