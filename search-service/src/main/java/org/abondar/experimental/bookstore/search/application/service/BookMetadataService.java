package org.abondar.experimental.bookstore.search.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.application.port.in.AddBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.application.port.out.BookMetadataRepository;
import org.abondar.experimental.bookstore.search.domain.BookMetadata;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookMetadataService implements AddBookMetadataUseCase, DeleteBookUseCase {

  private final BookMetadataRepository bookMetadataRepository;

  @Override
  public void deleteBook(String bookId) {
    bookMetadataRepository.deleteBook(bookId);
  }

  @Override
  public void upsertBookMetadata(BookMetadata bookMetadata) {
    bookMetadataRepository.addBookMetadata(bookMetadata);
  }
}
