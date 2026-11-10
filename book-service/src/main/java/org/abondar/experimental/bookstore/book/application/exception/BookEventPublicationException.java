package org.abondar.experimental.bookstore.book.application.exception;

public class BookEventPublicationException extends RuntimeException {

  public BookEventPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
