package org.abondar.experimental.bookstore.book.adapter.in;

import java.time.DateTimeException;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookContentStorageException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = BookApiAdapter.class)
public class BookApiExceptionHandler {

  @ExceptionHandler(BookContentStorageException.class)
  public ResponseEntity<Void> storageUnavailable(BookContentStorageException exception) {
    log.error("Book content storage operation failed", exception);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  @ExceptionHandler(BookAlreadyExistsException.class)
  public ResponseEntity<Void> bookAlreadyExists() {
    log.debug("Rejected book write because the ISBN already exists");
    return ResponseEntity.status(409).build();
  }

  @ExceptionHandler(BookNotFoundException.class)
  public ResponseEntity<Void> bookNotFound(BookNotFoundException exception) {
    log.debug("{}", exception.getMessage());
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
  public ResponseEntity<Void> invalidBook(RuntimeException exception) {
    log.debug("Rejected invalid book request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.badRequest().build();
  }
}
