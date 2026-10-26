package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;

public interface DeleteAuthorUseCase {
    void deleteAuthor(Id<Author> id);
}
