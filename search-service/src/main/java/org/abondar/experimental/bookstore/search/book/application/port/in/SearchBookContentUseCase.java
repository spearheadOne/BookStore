package org.abondar.experimental.bookstore.search.book.application.port.in;

import java.util.List;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;

public interface SearchBookContentUseCase {
  List<BookMetadataDocument> searchBookContent(BookSearchContentQuery query);
}
