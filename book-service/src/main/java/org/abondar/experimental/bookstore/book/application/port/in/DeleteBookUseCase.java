package org.abondar.experimental.bookstore.book.application.port.in;

import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public interface DeleteBookUseCase {
    void deleteBook(Id<Book> id);
}
