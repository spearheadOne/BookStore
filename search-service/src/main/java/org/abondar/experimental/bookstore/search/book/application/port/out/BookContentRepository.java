package org.abondar.experimental.bookstore.search.book.application.port.out;

import java.util.List;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;

public interface BookContentRepository {
  List<BookMetadataDocument> searchBookContent(BookSearchContentQuery query);

  void upsertBookContent(BookContentDocument document);
}
