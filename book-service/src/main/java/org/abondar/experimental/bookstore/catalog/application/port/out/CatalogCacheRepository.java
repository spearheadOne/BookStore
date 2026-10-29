package org.abondar.experimental.bookstore.catalog.application.port.out;

import java.util.Optional;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

public interface CatalogCacheRepository {
  void save(Catalog catalog);

  Optional<Catalog> findById(Id<Catalog> id);

  void delete(Id<Catalog> id);
}
