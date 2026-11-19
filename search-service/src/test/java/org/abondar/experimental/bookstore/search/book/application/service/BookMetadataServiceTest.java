package org.abondar.experimental.bookstore.search.book.application.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
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
  void searchReturnsMetadataDirectly() {
    var query = new BookSearchMetadataQuery("id", "0306406152", "catalog", "Java", 10);
    var books = List.of(new BookMetadataDocument("id", "0306406152", "Book", null, "catalog"));
    when(repository.searchBookMetadata(query)).thenReturn(books);
    assertSame(books, service.searchBookMetadata(query));
  }

  @Test
  void noMatchesReturnEmptyList() {
    var query = new BookSearchMetadataQuery(null, null, null, "missing", 10);
    when(repository.searchBookMetadata(query)).thenReturn(List.of());
    assertTrue(service.searchBookMetadata(query).isEmpty());
  }

  @Test
  void upsertsMetadata() {
    var book = new BookMetadataDocument("id", "0306406152", "Book", null, null);
    service.upsertBookMetadata(book);
    verify(repository).upsertBookMetadata(book);
  }

  @Test
  void deletesBook() {
    service.deleteBook("id");
    verify(repository).deleteBook("id");
  }

  @Test
  void propagatesSearchFailures() {
    var query = new BookSearchMetadataQuery(null, null, null, "Java", 10);
    var failure = new IllegalStateException("unavailable");
    when(repository.searchBookMetadata(query)).thenThrow(failure);
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> service.searchBookMetadata(query)));
  }

  @Test
  void propagatesWriteFailures() {
    var book = new BookMetadataDocument("id", "0306406152", "Book", null, null);
    var failure = new IllegalStateException("unavailable");
    doThrow(failure).when(repository).upsertBookMetadata(book);
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> service.upsertBookMetadata(book)));
    doThrow(failure).when(repository).deleteBook("id");
    assertSame(failure, assertThrows(IllegalStateException.class, () -> service.deleteBook("id")));
  }
}
