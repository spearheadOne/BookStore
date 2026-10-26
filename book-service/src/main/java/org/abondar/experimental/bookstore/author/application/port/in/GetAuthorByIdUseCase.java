package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;

import java.util.Optional;

public interface GetAuthorByIdUseCase {
    Optional<Author> getAuthorById(Id<Author> id);
}
