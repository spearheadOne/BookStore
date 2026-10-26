package org.abondar.experimental.bookstore.catalog.application.port.in;

import org.abondar.experimental.bookstore.catalog.domain.Catalog;

public interface SaveCatalogUseCase {
    void saveCatalog(Catalog catalog);
}
