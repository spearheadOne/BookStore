package org.abondar.experimental.bookstore.book.application.port.out;

import java.util.Optional;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public interface BookCacheRepository {

  void save(Book book);

  Optional<Book> findById(Id<Book> id);

  void delete(Id<Book> id);
}
