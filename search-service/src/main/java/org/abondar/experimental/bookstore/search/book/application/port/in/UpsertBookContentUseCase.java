package org.abondar.experimental.bookstore.search.book.application.port.in;

import org.abondar.experimental.bookstore.search.book.domain.BookContent;

public interface UpsertBookContentUseCase {
  void upsertBookContent(BookContent bookContent);
}
