package org.abondar.experimental.bookstore.search.book.application.service;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookMetadataService implements UpsertBookMetadataUseCase, DeleteBookUseCase {

  private final BookMetadataRepository bookMetadataRepository;

  @Override
  public void deleteBook(String bookId) {
    bookMetadataRepository.deleteBook(bookId);
  }

  @Override
  public void upsertBookMetadata(BookMetadata bookMetadata) {
    bookMetadataRepository.upsertBookMetadata(bookMetadata);
  }
}
