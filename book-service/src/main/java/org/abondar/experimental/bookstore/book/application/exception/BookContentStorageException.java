package org.abondar.experimental.bookstore.book.application.exception;

public class BookContentStorageException extends RuntimeException {

  public BookContentStorageException(String message) {
    super(message);
  }

  public BookContentStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
