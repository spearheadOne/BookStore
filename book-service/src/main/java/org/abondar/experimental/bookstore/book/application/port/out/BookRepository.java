package org.abondar.experimental.bookstore.book.application.port.out;

import java.util.List;
import java.util.Optional;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;

public interface BookRepository {
  void save(Book book);

  void update(Book book);

  void saveMany(List<Book> books);

  Optional<Book> findById(Id<Book> id);

  Optional<Book> findByIsbn(Isbn isbn);

  List<Book> findByAuthor(Id<Author> authorId);

  List<Book> findByCatalogId(Id<Catalog> catalogId);

  void delete(Id<Book> id);
}
