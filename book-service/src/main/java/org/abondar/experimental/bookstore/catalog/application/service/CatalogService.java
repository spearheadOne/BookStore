package org.abondar.experimental.bookstore.catalog.application.service;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogWithBooksUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.SaveCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.UpdateCatalogNameUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogRepository;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogService
        implements SaveCatalogUseCase,
        UpdateCatalogNameUseCase,
        GetCatalogUseCase,
        GetCatalogWithBooksUseCase,
        DeleteCatalogUseCase {

    private final CatalogRepository catalogRepository;

    private final BookRepository bookRepository;

    @Override
    public void saveCatalog(Catalog catalog) {
        var exists = catalogRepository.findByName(catalog.getName().value());
        if (exists.isPresent()) {
            throw new IllegalArgumentException("Catalog already exists");
        }

        catalogRepository.save(catalog);
    }

    @Override
    public void updateCatalogName(Id<Catalog> catalogId, String name) {
        var catalog = catalogRepository.findById(catalogId);
        if (catalog.isEmpty()) {
            throw new IllegalArgumentException("Catalog not found");
        }

        var updatedName = new Text(name);
        var existingName = catalogRepository.findByName(updatedName.value());
        if (existingName.isPresent() && !existingName.get().getId().equals(catalogId)) {
            throw new IllegalArgumentException("Name already exists");
        }

        catalogRepository.update(
                Catalog.restore(catalogId, updatedName, catalog.get().getCreatedAt(), Instant.now()));
    }

    @Override
    public Optional<Catalog> getCatalog(Id<Catalog> catalogId) {
        return catalogRepository.findById(catalogId);
    }

    @Override
    public Optional<CatalogWithBooks> getCatalogWithBooks(Id<Catalog> catalogId) {
        var catalog = catalogRepository.findById(catalogId);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }

        var books = bookRepository.findByCatalogId(catalogId);

        return Optional.of(new CatalogWithBooks(catalog.get(), books));
    }

    @Override
    public void deleteCatalog(Id<Catalog> catalogId) {
        catalogRepository.delete(catalogId);
    }
}
