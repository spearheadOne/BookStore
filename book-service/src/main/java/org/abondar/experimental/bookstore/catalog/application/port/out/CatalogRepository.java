package org.abondar.experimental.bookstore.catalog.application.port.out;

import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface CatalogRepository {

    void save(Catalog catalog);

    Optional<Catalog> findById(Id<Catalog> catalogId);

    void delete(Id<Catalog> catalogId);
}
