package org.abondar.experimental.bookstore.search.book.application.service;

import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookStorageRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookContentService implements SearchBookContentUseCase, UpsertBookContentUseCase {
  private final BookContentRepository repository;
  private final BookStorageRepository storageRepository;
  private final BookContentParser parser;

  @Override
  public List<BookMetadataDocument> searchBookContent(BookSearchContentQuery query) {
    return repository.searchBookContent(query);
  }

  @Override
  public void upsertBookContent(BookContent content) {
    try (var stream = storageRepository.downloadBookContent(content)) {
      repository.upsertBookContent(parser.parse(content, stream));
    } catch (IOException exception) {
      throw new BookMetadataEventException(
          "Could not close content stream for book " + content.bookId(), exception);
    }
  }
}
