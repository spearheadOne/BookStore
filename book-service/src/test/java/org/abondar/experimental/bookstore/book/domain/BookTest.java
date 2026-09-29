package org.abondar.experimental.bookstore.book.domain;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BookTest {

    @Test
    void createsBook() {
        var isbn = new Isbn("0306406152");
        var title = new Text("test");
        var description = "test";
        var issueYear = Year.of(2026);
        List<Id<Author>> authorIds = List.of(Id.generate(), Id.generate());
        Id<Catalog> catalogId = Id.generate();

        var book = Book.create(
                isbn,
                title,
                description,
                issueYear,
                authorIds,
                catalogId
        );

        assertAll(
                () -> assertNotNull(book.getId()),
                () -> assertEquals(isbn, book.getIsbn()),
                () -> assertEquals(title, book.getTitle()),
                () -> assertEquals(description, book.getDescription()),
                () -> assertEquals(issueYear, book.getIssueYear()),
                () -> assertEquals(authorIds, book.getAuthorIds()),
                () -> assertEquals(catalogId, book.getCatalogId()),
                () -> assertNotNull(book.getCreatedAt()),
                () -> assertEquals(book.getCreatedAt(), book.getUpdatedAt())
        );
    }

    @Test
    void createsBookWithNullables() {
        var isbn = new Isbn("0306406152");
        var title = new Text("test");

        var book = Book.create(
                isbn,
                title,
                null,
                null,
                List.of(),
                null
        );

        assertAll(
                () -> assertNotNull(book.getId()),
                () -> assertEquals(isbn, book.getIsbn()),
                () -> assertEquals(title, book.getTitle()),
                () -> assertNull(book.getDescription()),
                () -> assertNull(book.getIssueYear()),
                () -> assertEquals(List.of(), book.getAuthorIds()),
                () -> assertNull(book.getCatalogId()),
                () -> assertNotNull(book.getCreatedAt()),
                () -> assertEquals(book.getCreatedAt(), book.getUpdatedAt())
        );
    }

    @Test
    void rejectsNullIsbn() {
        assertThrows(
                NullPointerException.class,
                () -> Book.create(
                        null,
                        new Text("test"),
                        null,
                        null,
                        List.of(),
                        null
                )
        );
    }

    @Test
    void rejectsNullTitle() {
        assertThrows(
                NullPointerException.class,
                () -> Book.create(
                        new Isbn("0306406152"),
                        null,
                        null,
                        null,
                        List.of(),
                        null
                )
        );
    }

    @Test
    void rejectsNullAuthorIds() {
        assertThrows(
                NullPointerException.class,
                () -> Book.create(
                        new Isbn("0306406152"),
                        new Text("test"),
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void restoresBook() {
        var isbn = new Isbn("0306406152");
        var title = new Text("test");
        var description = "test";
        var issueYear = Year.of(2026);
        List<Id<Author>> authorIds = List.of(Id.generate(), Id.generate());
        Id<Catalog> catalogId = Id.generate();


        var book = Book.create(
                isbn,
                title,
                description,
                issueYear,
                authorIds,
                catalogId
        );

        var restored = Book.restore(
                book.getId(),
                isbn,
                title,
                description,
                issueYear,
                authorIds,
                catalogId,
                book.getCreatedAt(),
                book.getUpdatedAt()
        );

        assertAll(
                () -> assertEquals(book.getId(), restored.getId()),
                () -> assertEquals(book.getIsbn(), restored.getIsbn()),
                () -> assertEquals(book.getTitle(), restored.getTitle()),
                () -> assertEquals(book.getDescription(), restored.getDescription()),
                () -> assertEquals(book.getIssueYear(), restored.getIssueYear()),
                () -> assertEquals(authorIds, restored.getAuthorIds()),
                () -> assertEquals(catalogId, restored.getCatalogId()),
                () -> assertEquals(book.getCreatedAt(), restored.getCreatedAt()),
                () -> assertEquals(book.getUpdatedAt(), restored.getUpdatedAt())
        );
    }

    @Test
    void rejectsUpdateTimeBeforeCreationTime() {
        var createdAt = Instant.parse("2026-01-01T12:00:00Z");
        var updatedAt = createdAt.minusSeconds(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> Book.restore(
                        Id.generate(),
                        new Isbn("0306406152"),
                        new Text("Test"),
                        null,
                        Year.of(2026),
                        List.of(),
                        null,
                        createdAt,
                        updatedAt
                )
        );
    }

    @Test
    void copiesAuthorIdsAndPreservesOrder() {
        Id<Author> first = Id.generate();
        Id<Author> second = Id.generate();
        var supplied = new ArrayList<>(List.of(first, second));

        var book = Book.create(
                new Isbn("0306406152"),
                new Text("Test"),
                null,
                null,
                supplied,
                null
        );
        supplied.clear();

        assertEquals(List.of(first, second), book.getAuthorIds());
        assertThrows(UnsupportedOperationException.class, () -> book.getAuthorIds().clear());
    }

    @Test
    void rejectsDuplicateAuthorIds() {
        Id<Author> authorId = Id.generate();

        assertThrows(IllegalArgumentException.class, () ->
                Book.create(
                        new Isbn("0306406152"),
                        new Text("Test"),
                        null,
                        null,
                        List.of(authorId, authorId),
                        null
                )
        );
    }


}
