package org.abondar.experimental.bookstore.book.application.service;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookContentUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookCommand;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookService
    implements RegisterBookUseCase, GetBookUseCase, UpdateBookUseCase, DeleteBookUseCase {

  private final BookRepository bookRepository;
  private final BookCacheRepository bookCacheRepository;
  private final DeleteBookContentUseCase deleteBookContentUseCase;

  @Override
  public void registerBook(Book book) {
    var exists = bookRepository.findByIsbn(book.getIsbn()).isPresent();

    if (!exists) {
      bookRepository.save(book);
      log.debug("Registered book {}", book.getId().value());
      bookCacheRepository.save(book);
    } else {
      throw new BookAlreadyExistsException(book.getIsbn());
    }
  }

  @Override
  public Optional<Book> getBook(Id<Book> id) {
    var book = bookCacheRepository.findById(id);
    if (book.isPresent()) {
      return book;
    }
    book = bookRepository.findById(id);
    book.ifPresent(bookCacheRepository::save);
    return book;
  }

  @Override
  public Book updateBook(UpdateBookCommand command) {
    var existing =
        bookRepository
            .findById(command.id())
            .orElseThrow(() -> new BookNotFoundException(command.id()));
    var now = Instant.now();
    var updated =
        Book.restore(
            command.id(),
            command.isbn() != null ? command.isbn() : existing.getIsbn(),
            command.title() != null ? command.title() : existing.getTitle(),
            command.description() != null ? command.description() : existing.getDescription(),
            command.issueYear() != null ? command.issueYear() : existing.getIssueYear(),
            command.authorIds() != null ? command.authorIds() : existing.getAuthorIds(),
            command.catalogId() != null ? command.catalogId() : existing.getCatalogId(),
            existing.getCreatedAt(),
            now.isAfter(existing.getUpdatedAt()) ? now : existing.getUpdatedAt());

    bookRepository.update(updated);
    log.debug("Updated book {}", command.id().value());
    bookCacheRepository.save(updated);
    return updated;
  }

  @Override
  public void deleteBook(Id<Book> id) {
    boolean deleted =
        bookRepository.withBookLocked(
            id,
            () -> {
              deleteBookContentUseCase.deleteBookContent(id.value());
              bookRepository.delete(id);
            });
    if (deleted) {
      log.debug("Deleted book {}", id.value());
    } else {
      log.debug("Book {} was already absent during deletion", id.value());
    }
    bookCacheRepository.delete(id);
  }
}
