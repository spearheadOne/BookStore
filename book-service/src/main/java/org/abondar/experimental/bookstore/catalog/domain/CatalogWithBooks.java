package org.abondar.experimental.bookstore.catalog.domain;

import java.util.List;
import java.util.Objects;
import org.abondar.experimental.bookstore.book.domain.Book;

public record CatalogWithBooks(Catalog catalog, List<Book> books) {
  public CatalogWithBooks {
    Objects.requireNonNull(catalog, "Catalog must not be null");
    books = List.copyOf(Objects.requireNonNull(books, "Books must not be null"));
  }
}
