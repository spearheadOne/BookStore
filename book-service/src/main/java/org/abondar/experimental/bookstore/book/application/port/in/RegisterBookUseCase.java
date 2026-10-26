package org.abondar.experimental.bookstore.book.application.port.in;

import org.abondar.experimental.bookstore.book.domain.Book;

public interface RegisterBookUseCase {
    void registerBook(Book book);
}
