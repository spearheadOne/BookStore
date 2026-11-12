package org.abondar.experimental.bookstore.search.application.exception;

public class IndexCreationFailedException extends RuntimeException {
  public IndexCreationFailedException() {
    super("Failed to create index");
  }

  public IndexCreationFailedException(String index, Throwable cause) {
    super("Failed to create index " + index, cause);
  }
}
