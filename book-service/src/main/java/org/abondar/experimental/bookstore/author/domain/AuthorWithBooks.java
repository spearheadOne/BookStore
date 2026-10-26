package org.abondar.experimental.bookstore.author.domain;

import java.util.List;
import java.util.Objects;
import org.abondar.experimental.bookstore.book.domain.Book;

public record AuthorWithBooks(Author author, List<Book> books) {
  public AuthorWithBooks {
    Objects.requireNonNull(author, "Author must not be null");
    books = List.copyOf(Objects.requireNonNull(books, "Books must not be null"));
  }
}
