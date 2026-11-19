package org.abondar.experimental.bookstore.search.book.application.port.in;

import java.util.List;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;

public interface SearchBookMetadataUseCase {
  List<BookMetadataDocument> searchBookMetadata(BookSearchMetadataQuery query);
}
