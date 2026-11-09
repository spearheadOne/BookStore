package org.abondar.experimental.bookstore.book.adapter.in;

import java.time.DateTimeException;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookContentStorageException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BookApiAdapter.class)
public class BookApiExceptionHandler {

  @ExceptionHandler(BookContentStorageException.class)
  public ResponseEntity<Void> storageUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  @ExceptionHandler(BookAlreadyExistsException.class)
  public ResponseEntity<Void> bookAlreadyExists() {
    return ResponseEntity.status(409).build();
  }

  @ExceptionHandler(BookNotFoundException.class)
  public ResponseEntity<Void> bookNotFound() {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
  public ResponseEntity<Void> invalidBook() {
    return ResponseEntity.badRequest().build();
  }
}
