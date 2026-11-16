package org.abondar.experimental.bookstore.search.index.application.exception;

public class IndexCreationFailedException extends RuntimeException {
  public IndexCreationFailedException() {
    super("Failed to create index");
  }

  public IndexCreationFailedException(String index, Throwable cause) {
    super("Failed to create index " + index, cause);
  }
}
