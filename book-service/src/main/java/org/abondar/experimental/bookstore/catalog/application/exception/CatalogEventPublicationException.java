package org.abondar.experimental.bookstore.catalog.application.exception;

public class CatalogEventPublicationException extends RuntimeException {
  public CatalogEventPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
