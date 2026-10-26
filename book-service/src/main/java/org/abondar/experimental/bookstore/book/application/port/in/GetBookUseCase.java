package org.abondar.experimental.bookstore.book.application.port.in;

import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface GetBookUseCase {
    Optional<Book> getBook(Id<Book> id);
}
