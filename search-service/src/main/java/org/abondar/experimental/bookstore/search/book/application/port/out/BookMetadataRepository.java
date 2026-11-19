package org.abondar.experimental.bookstore.search.book.application.port.out;

import java.util.List;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;

public interface BookMetadataRepository {
  List<BookMetadataDocument> searchBookMetadata(BookSearchMetadataQuery query);

  void upsertBookMetadata(BookMetadataDocument document);

  void deleteBook(String bookId);

  void unlinkCatalogId(String catalogId);
}
