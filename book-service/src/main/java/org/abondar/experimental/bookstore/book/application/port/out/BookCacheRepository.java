package org.abondar.experimental.bookstore.book.application.port.out;

import java.util.List;
import java.util.Optional;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public interface BookCacheRepository {

  void save(Book book);

  void saveMany(List<Book> books);

  Optional<Book> findById(Id<Book> id);

  List<Book> findByIds(List<Id<Book>> ids);

  void delete(Id<Book> id);

  void deleteByIds(List<Id<Book>> ids);
}
