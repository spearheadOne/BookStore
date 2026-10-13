package org.abondar.experimental.bookstore.book.application.port.in;

import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface BookOperations {

    void registerBook(Book book);

    Optional<Book> getBook(Id<Book> id);

    void updateBook(Book book);

    void deleteBook(Id<Book> id);

}
