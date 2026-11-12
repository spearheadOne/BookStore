package org.abondar.experimental.bookstore.search.application.exception;

public class IndexDeletionFailedException extends RuntimeException {
  public IndexDeletionFailedException() {
    super("Failed to delete index");
  }

  public IndexDeletionFailedException(Throwable cause) {
    super("Failed to delete configured indexes", cause);
  }
}
