package org.abondar.experimental.bookstore.author.adapter.in;

import java.time.DateTimeException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorAlreadyExistsException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorNotFoundException;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthorApiAdapter.class)
public class AuthorApiExceptionHandler {

  @ExceptionHandler({
    AuthorAlreadyExistsException.class,
    BookAlreadyExistsException.class,
    DuplicateKeyException.class
  })
  public ResponseEntity<Void> authorAlreadyExists() {
    return ResponseEntity.status(409).build();
  }

  @ExceptionHandler(AuthorNotFoundException.class)
  public ResponseEntity<Void> authorNotFound() {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
  public ResponseEntity<Void> invalidAuthor() {
    return ResponseEntity.badRequest().build();
  }
}
