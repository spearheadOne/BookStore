package org.abondar.experimental.bookstore.search.book.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookMetadataService
    implements SearchBookMetadataUseCase, UpsertBookMetadataUseCase, DeleteBookUseCase {
  private final BookMetadataRepository repository;

  @Override
  public List<BookMetadataDocument> searchBookMetadata(BookSearchMetadataQuery query) {
    return repository.searchBookMetadata(query);
  }

  @Override
  public void upsertBookMetadata(BookMetadataDocument document) {
    repository.upsertBookMetadata(document);
  }

  @Override
  public void deleteBook(String bookId) {
    repository.deleteBook(bookId);
  }
}
