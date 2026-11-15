package org.abondar.experimental.bookstore.search.application.port.out;

import org.abondar.experimental.bookstore.search.domain.BookMetadata;

public interface BookMetadataRepository {

  void deleteBook(String bookId);

  void addBookMetadata(BookMetadata bookMetadata);
}
