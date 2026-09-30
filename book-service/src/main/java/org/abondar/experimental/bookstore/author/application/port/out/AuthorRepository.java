package org.abondar.experimental.bookstore.author.application.port.out;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;

import java.util.List;
import java.util.Optional;

public interface AuthorRepository {
    void save(Author author);

    Optional<Author> findById(Id<Author> id);

    List<Author> findBySortName(String sortName);

    void delete(Id<Author> id);
}
