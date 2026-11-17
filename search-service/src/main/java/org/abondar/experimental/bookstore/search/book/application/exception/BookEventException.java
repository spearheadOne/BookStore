package org.abondar.experimental.bookstore.search.book.application.exception;

public class BookEventException extends RuntimeException {

  public BookEventException(String message) {
    super(message);
  }

  public BookEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
