package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;

public interface UpdateAuthorUseCase {
    void updateAuthor(Author author);
}
