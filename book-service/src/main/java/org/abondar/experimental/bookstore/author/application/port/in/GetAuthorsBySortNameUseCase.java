package org.abondar.experimental.bookstore.author.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;

import java.util.List;

public interface GetAuthorsBySortNameUseCase {
    List<Author> getBySortName(String sortName);
}
