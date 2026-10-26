package org.abondar.experimental.bookstore.author.application.exception;

public class AuthorAlreadyExistsException extends RuntimeException {

  public AuthorAlreadyExistsException() {
    super("Author already exists");
  }
}
