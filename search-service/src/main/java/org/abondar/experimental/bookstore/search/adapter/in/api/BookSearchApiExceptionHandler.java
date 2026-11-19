package org.abondar.experimental.bookstore.search.adapter.in.api;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.book.application.exception.BookSearchException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = BookSearchApiAdapter.class)
public class BookSearchApiExceptionHandler {

  @ExceptionHandler(BookSearchException.class)
  public ResponseEntity<Void> searchUnavailable(BookSearchException exception) {
    log.error("Book search failed", exception);
    return ResponseEntity.status(503).build();
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HandlerMethodValidationException.class
  })
  public ResponseEntity<Void> invalidSearchRequest(RuntimeException exception) {
    log.debug("Rejected invalid book search request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.badRequest().build();
  }
}
