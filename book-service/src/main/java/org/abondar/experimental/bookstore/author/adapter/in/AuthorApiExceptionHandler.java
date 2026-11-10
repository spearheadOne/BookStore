package org.abondar.experimental.bookstore.author.adapter.in;

import java.time.DateTimeException;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.author.application.exception.AuthorAlreadyExistsException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorNotFoundException;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = AuthorApiAdapter.class)
public class AuthorApiExceptionHandler {

  @ExceptionHandler({
    AuthorAlreadyExistsException.class,
    BookAlreadyExistsException.class,
    DuplicateKeyException.class
  })
  public ResponseEntity<Void> authorAlreadyExists(RuntimeException exception) {
    log.debug("Rejected conflicting author request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.status(409).build();
  }

  @ExceptionHandler({AuthorNotFoundException.class, BookNotFoundException.class})
  public ResponseEntity<Void> authorNotFound(RuntimeException exception) {
    log.debug("{}", exception.getMessage());
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
  public ResponseEntity<Void> invalidAuthor(RuntimeException exception) {
    log.debug("Rejected invalid author request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.badRequest().build();
  }
}
