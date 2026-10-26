package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface GetAuthorWithBooksByIdUseCase {
    Optional<AuthorWithBooks> getAuthorWithBooksById(Id<Author> id);
}
