package org.abondar.experimental.bookstore.book.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;

import java.time.Instant;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Book {

    @EqualsAndHashCode.Include
    private final Id<Book> id;

    private final Isbn isbn;
    private final Text title;
    private final String description;
    private final Year issueYear;
    private final List<Id<Author>> authorIds;
    private final Id<Catalog> catalogId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Book(
            Id<Book> id,
            Isbn isbn,
            Text title,
            String description,
            Year issueYear,
            List<Id<Author>> authorIds,
            Id<Catalog> catalogId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.isbn = Objects.requireNonNull(isbn);
        this.title = Objects.requireNonNull(title);
        this.description = description;
        this.issueYear = issueYear;

        this.authorIds = List.copyOf(Objects.requireNonNull(authorIds, "Author IDs must not be null"));
        if (new HashSet<>(this.authorIds).size() != this.authorIds.size()) {
            throw new IllegalArgumentException("Author IDs must not contain duplicates");
        }

        this.catalogId = catalogId;

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Creation time must not be null"
        );
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Update time must not be null"
        );

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Updated time cannot precede creation time"
            );
        }
    }

    public static Book create(
            Isbn isbn,
            Text title,
            String description,
            Year issueYear,
            List<Id<Author>> authorIds,
            Id<Catalog> catalogId
    ) {
        var now = Instant.now();

        return new Book(
                Id.generate(),
                isbn,
                title,
                description,
                issueYear,
                authorIds,
                catalogId,
                now,
                now
        );
    }

    public static Book restore(
            Id<Book> id,
            Isbn isbn,
            Text title,
            String description,
            Year issueYear,
            List<Id<Author>> authorIds,
            Id<Catalog> catalogId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Book(
                id,
                isbn,
                title,
                description,
                issueYear,
                authorIds,
                catalogId,
                createdAt,
                updatedAt
        );
    }


}
