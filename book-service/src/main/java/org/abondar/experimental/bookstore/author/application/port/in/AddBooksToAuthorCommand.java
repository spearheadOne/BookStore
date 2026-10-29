package org.abondar.experimental.bookstore.author.application.port.in;

import java.util.List;
import java.util.Objects;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;

public record AddBooksToAuthorCommand(Id<Author> authorId, List<Id<Book>> bookIds) {
  public AddBooksToAuthorCommand {
    Objects.requireNonNull(authorId, "Author ID must not be null");
    bookIds = List.copyOf(Objects.requireNonNull(bookIds, "Book IDs must not be null"));
  }
}
