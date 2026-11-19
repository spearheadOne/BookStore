package org.abondar.experimental.bookstore.search.catalog.adapter.in.api;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogSearchException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = CatalogApiAdapter.class)
public class CatalogSearchExceptionHandler {

  @ExceptionHandler(CatalogSearchException.class)
  public ResponseEntity<Void> searchUnavailable(CatalogSearchException exception) {
    log.error("Catalog search failed", exception);
    return ResponseEntity.status(503).build();
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HandlerMethodValidationException.class
  })
  public ResponseEntity<Void> invalidSearchRequest(RuntimeException exception) {
    log.debug("Rejected invalid catalog search request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.badRequest().build();
  }
}
