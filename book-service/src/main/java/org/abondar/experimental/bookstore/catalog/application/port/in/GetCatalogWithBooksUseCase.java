package org.abondar.experimental.bookstore.catalog.application.port.in;

import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface GetCatalogWithBooksUseCase {
    Optional<CatalogWithBooks> getCatalogWithBooks(Id<Catalog> catalogId);
}
