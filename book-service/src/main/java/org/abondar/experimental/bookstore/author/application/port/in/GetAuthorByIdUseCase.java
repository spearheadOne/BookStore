package org.abondar.experimental.bookstore.author.application.port.in;

import java.util.Optional;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;

public interface GetAuthorByIdUseCase {
  Optional<Author> getAuthorById(Id<Author> id);
}
