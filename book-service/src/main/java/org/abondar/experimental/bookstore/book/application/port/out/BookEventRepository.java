package org.abondar.experimental.bookstore.book.application.port.out;

import java.util.List;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public interface BookEventRepository {

  void sendBookUpsert(Book book);

  void sendManyUpserts(List<Book> books);

  void sendBookDeleted(Id<Book> id);

  void sendBookContentAvailable(
      Id<Book> id,
      String format,
      String storageKey,
      String contentType,
      String fileName,
      long size);
}
