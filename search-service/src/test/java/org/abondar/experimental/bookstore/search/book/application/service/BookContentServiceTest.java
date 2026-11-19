package org.abondar.experimental.bookstore.search.book.application.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.abondar.experimental.bookstore.search.book.application.exception.BookContentEventException;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookStorageRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookContentServiceTest {

  @Mock private BookContentRepository repository;
  @Mock private BookStorageRepository storageRepository;
  @Mock private BookContentParser parser;
  @Mock private InputStream stream;
  @InjectMocks private BookContentService service;

  private final BookContent content =
      new BookContent("book-id", "pdf", "key", "application/pdf", null, 100);

  @Test
  void parsesIndexesAndClosesDownload() throws IOException {
    var document = new BookContentDocument("book-id", "pdf", null, "Text");
    when(storageRepository.downloadBookContent(content)).thenReturn(stream);
    when(parser.parse(content, stream)).thenReturn(document);

    service.upsertBookContent(content);

    verify(repository).upsertBookContent(document);
    verify(stream).close();
  }

  @Test
  void parseFailureClosesDownloadAndDoesNotIndex() throws IOException {
    var failure = new BookContentEventException("Invalid book", new IllegalStateException());
    when(storageRepository.downloadBookContent(content)).thenReturn(stream);
    when(parser.parse(content, stream)).thenThrow(failure);

    assertSame(
        failure,
        assertThrows(BookContentEventException.class, () -> service.upsertBookContent(content)));

    verify(stream).close();
    verifyNoInteractions(repository);
  }

  @Test
  void indexingFailureClosesDownload() throws IOException {
    var document = new BookContentDocument("book-id", "pdf", null, "Text");
    var failure = new BookMetadataEventException("OpenSearch unavailable");
    when(storageRepository.downloadBookContent(content)).thenReturn(stream);
    when(parser.parse(content, stream)).thenReturn(document);
    doThrow(failure).when(repository).upsertBookContent(document);

    assertSame(
        failure,
        assertThrows(BookMetadataEventException.class, () -> service.upsertBookContent(content)));

    verify(stream).close();
  }

  @Test
  void closeFailureRetainsCauseForRetries() throws IOException {
    when(storageRepository.downloadBookContent(content)).thenReturn(stream);
    var cause = new IOException("Connection close failed");
    doThrow(cause).when(stream).close();

    var failure =
        assertThrows(BookMetadataEventException.class, () -> service.upsertBookContent(content));

    assertSame(cause, failure.getCause());
  }

  @Test
  void contentSearchReturnsRepositoryMetadata() {
    var query = new BookSearchContentQuery(null, "pdf", null, "Java", 10);
    var books = List.of(new BookMetadataDocument("one", "0306406152", "Book", null, null));
    when(repository.searchBookContent(query)).thenReturn(books);
    assertSame(books, service.searchBookContent(query));
    verify(repository).searchBookContent(query);
    verifyNoInteractions(storageRepository, parser);
  }

  @Test
  void emptyContentSearchReturnsEmptyMetadataList() {
    var query = new BookSearchContentQuery(null, null, null, "missing", 10);
    var books = List.<BookMetadataDocument>of();
    when(repository.searchBookContent(query)).thenReturn(books);
    assertSame(books, service.searchBookContent(query));
    verifyNoInteractions(storageRepository, parser);
  }
}
