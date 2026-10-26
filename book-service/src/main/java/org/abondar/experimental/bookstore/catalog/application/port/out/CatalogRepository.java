package org.abondar.experimental.bookstore.catalog.application.port.out;

import java.util.Optional;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

public interface CatalogRepository {

  void save(Catalog catalog);

  void update(Catalog catalog);

  Optional<Catalog> findById(Id<Catalog> catalogId);

  Optional<Catalog> findByName(String name);

  void delete(Id<Catalog> catalogId);
}
