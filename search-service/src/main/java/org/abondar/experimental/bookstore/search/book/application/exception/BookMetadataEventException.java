package org.abondar.experimental.bookstore.search.book.application.exception;

public class BookMetadataEventException extends RuntimeException {

  public BookMetadataEventException(String message) {
    super(message);
  }

  public BookMetadataEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
