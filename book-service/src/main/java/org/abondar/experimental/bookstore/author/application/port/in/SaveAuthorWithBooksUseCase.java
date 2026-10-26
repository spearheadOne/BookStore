package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.domain.Book;

import java.util.List;

public interface SaveAuthorWithBooksUseCase {
    void saveAuthorWithBooks(Author author, List<Book> books);
}
