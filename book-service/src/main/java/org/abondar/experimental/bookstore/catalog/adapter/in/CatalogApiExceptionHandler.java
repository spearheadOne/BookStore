package org.abondar.experimental.bookstore.catalog.adapter.in;

import org.abondar.experimental.bookstore.catalog.application.exception.CatalogAlreadyExistsException;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.DateTimeException;

@RestControllerAdvice(assignableTypes = CatalogApiAdapter.class)
public class CatalogApiExceptionHandler {

    @ExceptionHandler({CatalogAlreadyExistsException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Void> catalogConflict() {
        return ResponseEntity.status(409).build();
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    public ResponseEntity<Void> catalogNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    public ResponseEntity<Void> invalidCatalog() {
        return ResponseEntity.badRequest().build();
    }
}
