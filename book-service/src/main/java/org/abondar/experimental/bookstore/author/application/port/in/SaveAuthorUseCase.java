package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;

public interface SaveAuthorUseCase {
    void saveAuthor(Author author);
}
