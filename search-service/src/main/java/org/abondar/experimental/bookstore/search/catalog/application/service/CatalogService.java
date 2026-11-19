package org.abondar.experimental.bookstore.search.catalog.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.CatalogSearchQuery;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.SearchCatalogUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.UpsertCatalogUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.port.out.CatalogRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogService
    implements UpsertCatalogUseCase, DeleteCatalogUseCase, SearchCatalogUseCase {

  private final CatalogRepository catalogRepository;

  private final BookMetadataRepository bookMetadataRepository;

  @Override
  public void upsertCatalog(CatalogDocument catalogDocument) {
    catalogRepository.upsertCatalog(catalogDocument.catalogId(), catalogDocument.name());
  }

  @Override
  public void deleteCatalog(String catalogId) {
    catalogRepository.deleteCatalog(catalogId);
    bookMetadataRepository.unlinkCatalogId(catalogId);
  }

  @Override
  public List<CatalogDocument> searchCatalogs(CatalogSearchQuery query) {
    return catalogRepository.searchCatalogs(query);
  }
}
