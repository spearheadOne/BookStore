package org.abondar.experimental.bookstore.catalog.application.port.in;

import java.util.Optional;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

public interface GetCatalogUseCase {
  Optional<Catalog> getCatalog(Id<Catalog> catalogId);
}
