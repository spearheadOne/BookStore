package org.abondar.experimental.bookstore.search.book.application.port.out;

import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;

public interface BookMetadataRepository {

  void deleteBook(String bookId);

  void upsertBookMetadata(BookMetadata bookMetadata);

  void unlinkCatalogId(String catalogId);
}
