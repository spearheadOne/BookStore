package org.abondar.experimental.bookstore.catalog.application.service;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogAlreadyExistsException;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogNotFoundException;
import org.abondar.experimental.bookstore.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.GetCatalogWithBooksUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.SaveCatalogUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.in.UpdateCatalogNameUseCase;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogCacheRepository;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogRepository;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.catalog.domain.CatalogWithBooks;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.stereotype.Service;

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

  private final CatalogCacheRepository catalogCacheRepository;

  @Override
  public void saveCatalog(Catalog catalog) {
    var exists = catalogRepository.findByName(catalog.getName().value());
    if (exists.isPresent()) {
      throw new CatalogAlreadyExistsException(catalog.getName().value());
    }

    catalogRepository.save(catalog);
    catalogCacheRepository.save(catalog);
  }

  @Override
  public Catalog updateCatalogName(Id<Catalog> catalogId, String name) {
    var catalog = catalogRepository.findById(catalogId);
    if (catalog.isEmpty()) {
      throw new CatalogNotFoundException();
    }

    var updatedName = new Text(name);
    var existingName = catalogRepository.findByName(updatedName.value());
    if (existingName.isPresent() && !existingName.get().getId().equals(catalogId)) {
      throw new CatalogAlreadyExistsException(name);
    }
    var now = Instant.now();
    var updatedAt = now.isAfter(catalog.get().getUpdatedAt()) ? now : catalog.get().getUpdatedAt();
    var updated = Catalog.restore(catalogId, updatedName, catalog.get().getCreatedAt(), updatedAt);

    catalogRepository.update(updated);
    catalogCacheRepository.save(updated);

    return updated;
  }

  @Override
  public Optional<Catalog> getCatalog(Id<Catalog> catalogId) {
    var catalog = catalogCacheRepository.findById(catalogId);
    if (catalog.isPresent()) {
      return catalog;
    }

    catalog = catalogRepository.findById(catalogId);
    catalog.ifPresent(catalogCacheRepository::save);

    return catalog;
  }

  @Override
  public Optional<CatalogWithBooks> getCatalogWithBooks(Id<Catalog> catalogId) {
    return getCatalog(catalogId)
        .map(catalog -> new CatalogWithBooks(catalog, bookRepository.findByCatalogId(catalogId)));
  }

  @Override
  public void deleteCatalog(Id<Catalog> catalogId) {
    catalogRepository.delete(catalogId);
    catalogCacheRepository.delete(catalogId);
  }
}
