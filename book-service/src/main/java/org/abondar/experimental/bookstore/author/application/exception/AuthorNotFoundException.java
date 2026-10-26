package org.abondar.experimental.bookstore.author.application.exception;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;

public class AuthorNotFoundException extends RuntimeException {
  public AuthorNotFoundException(Id<Author> id) {
    super("Author " + id.value() + " does not exist");
  }
}
