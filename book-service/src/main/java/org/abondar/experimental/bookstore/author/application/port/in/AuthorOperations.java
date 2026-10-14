package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

import java.util.List;
import java.util.Optional;

public interface AuthorOperations {

    void saveAuthor(Author author);

    void saveAuthorWithBooks(Author author, List<Book> books);

    void updateAuthor(Author author);

    Optional<Author> getAuthorById(Id<Author> id);

    Optional<AuthorWithBooks> getAuthorWithBooksById(Id<Author> id);

    List<Author> getBySortName(String sortName);

    void deleteAuthor(Id<Author> id);

}
