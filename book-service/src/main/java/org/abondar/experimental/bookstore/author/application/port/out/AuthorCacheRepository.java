package org.abondar.experimental.bookstore.author.application.port.out;

import java.util.Optional;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;

public interface AuthorCacheRepository {

  void save(Author author);

  Optional<Author> findById(Id<Author> id);

  void delete(Id<Author> id);
}
