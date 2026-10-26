package org.abondar.experimental.bookstore.catalog.application.port.in;

import java.util.Optional;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;

public interface GetCatalogWithBooksUseCase {
  Optional<CatalogWithBooks> getCatalogWithBooks(Id<Catalog> catalogId);
}
