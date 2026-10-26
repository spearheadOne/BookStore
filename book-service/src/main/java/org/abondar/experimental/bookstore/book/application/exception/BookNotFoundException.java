package org.abondar.experimental.bookstore.book.application.exception;

import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public class BookNotFoundException extends RuntimeException {

  public BookNotFoundException(Id<Book> id) {
    super("Book " + id.value() + " does not exist");
  }
}
