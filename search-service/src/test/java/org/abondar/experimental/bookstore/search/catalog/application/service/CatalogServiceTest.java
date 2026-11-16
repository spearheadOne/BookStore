package org.abondar.experimental.bookstore.search.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.catalog.application.port.out.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

  @Mock private CatalogRepository repository;
  @Mock private BookMetadataRepository bookMetadataRepository;
  @InjectMocks private CatalogService service;

  @Test
  void upsertsCatalog() {
    service.upsertCatalog("catalog-id", "Catalog");
    verify(repository).upsertCatalog("catalog-id", "Catalog");
    verifyNoMoreInteractions(repository);
    verifyNoInteractions(bookMetadataRepository);
  }

  @Test
  void deletesCatalog() {
    service.deleteCatalog("catalog-id");
    var order = inOrder(repository, bookMetadataRepository);
    order.verify(repository).deleteCatalog("catalog-id");
    order.verify(bookMetadataRepository).unlinkCatalogId("catalog-id");
    verifyNoMoreInteractions(repository, bookMetadataRepository);
  }

  @Test
  void upsertFailurePropagatesToListener() {
    var failure = new UncheckedIOException(new IOException("Unavailable"));
    doThrow(failure).when(repository).upsertCatalog("catalog-id", "Catalog");
    assertSame(
        failure,
        assertThrows(
            UncheckedIOException.class, () -> service.upsertCatalog("catalog-id", "Catalog")));
  }

  @Test
  void deletionFailurePropagatesToListener() {
    var failure = new UncheckedIOException(new IOException("Unavailable"));
    doThrow(failure).when(repository).deleteCatalog("catalog-id");
    assertSame(
        failure,
        assertThrows(UncheckedIOException.class, () -> service.deleteCatalog("catalog-id")));
    verifyNoInteractions(bookMetadataRepository);
  }

  @Test
  void unlinkFailurePropagatesAfterCatalogDeletion() {
    var failure = new UncheckedIOException(new IOException("Cleanup unavailable"));
    doThrow(failure).when(bookMetadataRepository).unlinkCatalogId("catalog-id");

    assertSame(
        failure,
        assertThrows(UncheckedIOException.class, () -> service.deleteCatalog("catalog-id")));

    var order = inOrder(repository, bookMetadataRepository);
    order.verify(repository).deleteCatalog("catalog-id");
    order.verify(bookMetadataRepository).unlinkCatalogId("catalog-id");
  }

  @Test
  void failedCleanupCanBeRetriedAfterCatalogWasDeleted() {
    doThrow(new IllegalStateException("Partial update failed"))
        .doNothing()
        .when(bookMetadataRepository)
        .unlinkCatalogId("catalog-id");

    assertThrows(IllegalStateException.class, () -> service.deleteCatalog("catalog-id"));
    service.deleteCatalog("catalog-id");

    verify(repository, times(2)).deleteCatalog("catalog-id");
    verify(bookMetadataRepository, times(2)).unlinkCatalogId("catalog-id");
  }
}
