package org.abondar.experimental.bookstore.book.application.port.in;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;

import java.time.Year;
import java.util.List;
import java.util.Objects;

public record UpdateBookCommand(
        Id<Book> id,
        Isbn isbn,
        Text title,
        String description,
        Year issueYear,
        List<Id<Author>> authorIds,
        Id<Catalog> catalogId) {
    public UpdateBookCommand {
        Objects.requireNonNull(id, "Book ID must not be null");
        if (authorIds != null) {
            authorIds = List.copyOf(authorIds);
        }
    }
}
