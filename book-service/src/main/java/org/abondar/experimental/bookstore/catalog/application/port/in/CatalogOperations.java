package org.abondar.experimental.bookstore.catalog.application.port.in;

import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface CatalogOperations {

    void saveCatalog(Catalog catalog);

    void updateCatalogName(Id<Catalog> catalogId, String name);

    Optional<Catalog> getCatalog(Id<Catalog> catalogId);

    Optional<CatalogWithBooks> getCatalogWithBooks(Id<Catalog> catalogId);

    void deleteCatalog(Id<Catalog> catalogId);

}
