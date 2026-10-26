package org.abondar.experimental.bookstore.book.application.port.in;

import java.util.Optional;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public interface GetBookUseCase {
  Optional<Book> getBook(Id<Book> id);
}
