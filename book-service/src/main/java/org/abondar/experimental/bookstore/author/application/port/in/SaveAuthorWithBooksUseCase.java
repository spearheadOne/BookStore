package org.abondar.experimental.bookstore.author.application.port.in;

import java.util.List;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.domain.Book;

public interface SaveAuthorWithBooksUseCase {
    AuthorWithBooks saveAuthorWithBooks(Author author, List<Book> books);
}
