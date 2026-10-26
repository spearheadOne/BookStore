package org.abondar.experimental.bookstore.book.application.exception;

import org.abondar.experimental.bookstore.book.domain.Isbn;

public class BookAlreadyExistsException extends RuntimeException {

  public BookAlreadyExistsException(Isbn isbn) {
    super("Book with ISBN " + isbn.value() + " already exists");
  }

  public BookAlreadyExistsException(Isbn isbn, Throwable cause) {
    super("Book with ISBN " + isbn.value() + " already exists", cause);
  }
}
