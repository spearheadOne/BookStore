package org.abondar.experimental.bookstore.search.application.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.abondar.experimental.bookstore.search.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.domain.BookMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookMetadataServiceTest {

  @Mock private BookMetadataRepository repository;
  @InjectMocks private BookMetadataService service;

  @Test
  void upsertsMetadata() {
    var book = book();
    service.upsertBookMetadata(book);
    verify(repository).addBookMetadata(book);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void deletesMetadata() {
    service.deleteBook("book-id");
    verify(repository).deleteBook("book-id");
    verifyNoMoreInteractions(repository);
  }

  @Test
  void propagatesUpsertFailure() {
    var book = book();
    var failure = new UncheckedIOException(new IOException("Unavailable"));
    doThrow(failure).when(repository).addBookMetadata(book);
    assertSame(
        failure, assertThrows(UncheckedIOException.class, () -> service.upsertBookMetadata(book)));
  }

  @Test
  void propagatesDeletionFailure() {
    var failure = new UncheckedIOException(new IOException("Unavailable"));
    doThrow(failure).when(repository).deleteBook("book-id");
    assertSame(
        failure, assertThrows(UncheckedIOException.class, () -> service.deleteBook("book-id")));
  }

  private BookMetadata book() {
    return new BookMetadata("book-id", "0306406152", "Book", null, null);
  }
}
