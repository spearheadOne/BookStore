package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;

public interface AddBooksToAuthorUseCase {
  AuthorWithBooks addBooksToAuthor(AddBooksToAuthorCommand command);
}
