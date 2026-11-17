package org.abondar.experimental.bookstore.search.book.application.port.out;

import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;

public interface BookContentRepository {

  void upsertBookContent(BookContentDocument bookContentDocument);
}
