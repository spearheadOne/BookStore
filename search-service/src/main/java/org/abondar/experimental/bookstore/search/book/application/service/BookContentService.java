package org.abondar.experimental.bookstore.search.book.application.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.book.application.exception.BookEventException;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookStorageRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookContentService implements UpsertBookContentUseCase {

  private final BookContentRepository repository;

  private final BookStorageRepository storageRepository;

  private final BookContentParser parser;

  @Override
  public void upsertBookContent(BookContent bookContent) {
    try (var inputStream = storageRepository.downloadBookContent(bookContent)) {
      var document = parser.parse(bookContent, inputStream);
      repository.upsertBookContent(document);
    } catch (IOException exception) {
      throw new BookEventException(
          "Could not close content stream for book " + bookContent.bookId(), exception);
    }
  }
}
