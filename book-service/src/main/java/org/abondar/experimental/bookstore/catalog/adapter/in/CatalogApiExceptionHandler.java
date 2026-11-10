package org.abondar.experimental.bookstore.catalog.adapter.in;

import java.time.DateTimeException;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogAlreadyExistsException;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogEventPublicationException;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = CatalogApiAdapter.class)
public class CatalogApiExceptionHandler {

  @ExceptionHandler(CatalogEventPublicationException.class)
  public ResponseEntity<Void> eventPublicationUnavailable(
      CatalogEventPublicationException exception) {
    log.error("Catalog event publication failed", exception);
    return ResponseEntity.status(503).build();
  }

  @ExceptionHandler({CatalogAlreadyExistsException.class, DataIntegrityViolationException.class})
  public ResponseEntity<Void> catalogConflict(RuntimeException exception) {
    log.debug("Rejected conflicting catalog request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.status(409).build();
  }

  @ExceptionHandler(CatalogNotFoundException.class)
  public ResponseEntity<Void> catalogNotFound() {
    log.debug("Catalog not found");
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
  public ResponseEntity<Void> invalidCatalog(RuntimeException exception) {
    log.debug("Rejected invalid catalog request ({})", exception.getClass().getSimpleName());
    return ResponseEntity.badRequest().build();
  }
}
