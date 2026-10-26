package org.abondar.experimental.bookstore.author.application.port.in;

import java.util.List;
import org.abondar.experimental.bookstore.author.domain.Author;

public interface GetAuthorsBySortNameUseCase {
  List<Author> getBySortName(String sortName);
}
