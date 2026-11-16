package org.abondar.experimental.bookstore.search.catalog.application.service;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.UpsertCatalogUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.port.out.CatalogRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogService implements UpsertCatalogUseCase, DeleteCatalogUseCase {

  private final CatalogRepository catalogRepository;

  private final BookMetadataRepository bookMetadataRepository;

  @Override
  public void upsertCatalog(String catalogId, String catalogName) {
    catalogRepository.upsertCatalog(catalogId, catalogName);
  }

  @Override
  public void deleteCatalog(String catalogId) {
    catalogRepository.deleteCatalog(catalogId);
    bookMetadataRepository.unlinkCatalogId(catalogId);
  }
}
