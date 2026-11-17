package org.abondar.experimental.bookstore.book.application.exception;

public class InvalidBookReferenceException extends RuntimeException {

  public InvalidBookReferenceException(Throwable cause) {
    super("Referenced book, author or catalog does not exist", cause);
  }
}
