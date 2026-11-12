package org.abondar.experimental.bookstore.search.adapter.in.index;

import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.application.exception.IndexCreationFailedException;
import org.abondar.experimental.bookstore.search.application.exception.IndexDeletionFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = IndexApiAdapter.class)
public class IndexApiExceptionHandler {

  @ExceptionHandler({IndexCreationFailedException.class, IndexDeletionFailedException.class})
  public ResponseEntity<Void> indexOperationFailed(RuntimeException exception) {
    log.error("{}", exception.getMessage(), exception);
    return ResponseEntity.status(503).build();
  }
}
