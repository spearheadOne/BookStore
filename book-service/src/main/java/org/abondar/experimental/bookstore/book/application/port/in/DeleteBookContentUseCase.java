package org.abondar.experimental.bookstore.book.application.port.in;

import java.util.UUID;

public interface DeleteBookContentUseCase {
  /** Deletes all stored formats for this book. Already absent content is treated as deleted. */
  void deleteBookContent(UUID id);
}
