package org.abondar.experimental.bookstore.author.application.port.in;

import java.util.Optional;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.common.Id;

public interface GetAuthorWithBooksByIdUseCase {
  Optional<AuthorWithBooks> getAuthorWithBooksById(Id<Author> id);
}
