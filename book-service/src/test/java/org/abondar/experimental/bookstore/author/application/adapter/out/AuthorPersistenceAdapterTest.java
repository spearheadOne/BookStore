package org.abondar.experimental.bookstore.author.application.adapter.out;

import org.abondar.experimental.bookstore.BaseAdapterTest;
import org.abondar.experimental.bookstore.author.adapter.out.AuthorPersistenceAdapter;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.BOOK;
import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.BOOK_AUTHOR;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthorPersistenceAdapterTest extends BaseAdapterTest {

    @Autowired
    private AuthorPersistenceAdapter adapter;

    @Test
    public void saveAndFindAuthor() {
        var author = Author.create(
                new Text("Ada"),
                new Text("Lovelace"),
                "Lovelace, Ada",
                LocalDate.of(1815, 12, 10),
                LocalDate.of(1852, 11, 27),
                "GB",
                "Mathematician"
        );

        adapter.save(author);
        var foundAuthor = adapter.findById(author.getId());

        assertTrue(foundAuthor.isPresent());
        assertAll(
                () -> assertEquals(author.getId(), foundAuthor.get().getId()),
                () -> assertEquals(author.getFirstName(), foundAuthor.get().getFirstName()),
                () -> assertEquals(author.getLastName(), foundAuthor.get().getLastName()),
                () -> assertEquals(author.getSortName(), foundAuthor.get().getSortName()),
                () -> assertEquals(author.getDateOfBirth(), foundAuthor.get().getDateOfBirth()),
                () -> assertEquals(author.getDateOfDeath(), foundAuthor.get().getDateOfDeath()),
                () -> assertEquals(author.getCountryCode(), foundAuthor.get().getCountryCode()),
                () -> assertEquals(author.getBiography(), foundAuthor.get().getBiography()),
                () -> assertNotNull(foundAuthor.get().getCreatedAt()),
                () -> assertNotNull(foundAuthor.get().getUpdatedAt())
        );
    }

    @Test
    public void findMissingAuthor() {
        assertTrue(adapter.findById(Id.generate()).isEmpty());
    }

    @Test
    public void findAuthorByNameAndDob() {
        var author = Author.create(new Text("Named"), new Text("Writer"), null,
                LocalDate.of(1980, 4, 5), null, null, null);
        var otherAuthor = Author.create(new Text("Named"), new Text("Writer"), null,
                LocalDate.of(1981, 4, 5), null, null, null);
        adapter.save(author);
        adapter.save(otherAuthor);

        var foundAuthor = adapter.findByNameAndDob(author.getFirstName(), author.getLastName(),
                author.getDateOfBirth());

        assertTrue(foundAuthor.isPresent());
        assertEquals(author.getId(), foundAuthor.get().getId());
    }

    @Test
    public void findAuthorByNameWithUnknownDob() {
        var author = Author.create(new Text("Unknown"), new Text("Birthday"), null,
                null, null, null, null);
        adapter.save(author);

        var foundAuthor = adapter.findByNameAndDob(author.getFirstName(), author.getLastName(), null);

        assertTrue(foundAuthor.isPresent());
        assertEquals(author.getId(), foundAuthor.get().getId());
    }

    @Test
    public void updateAuthor() {
        var author = Author.create(new Text("Old"), new Text("Name"), "Name, Old",
                null, null, "DE", "Original biography");
        adapter.save(author);

        var updatedAuthor = Author.restore(author.getId(), new Text("New"), new Text("Name"),
                "Name, New", LocalDate.of(1900, 1, 1), null, null, null,
                author.getCreatedAt(), author.getUpdatedAt().plusSeconds(1));
        adapter.save(updatedAuthor);
        var foundAuthor = adapter.findById(author.getId());

        assertTrue(foundAuthor.isPresent());
        assertAll(
                () -> assertEquals(updatedAuthor.getFirstName(), foundAuthor.get().getFirstName()),
                () -> assertEquals(updatedAuthor.getSortName(), foundAuthor.get().getSortName()),
                () -> assertEquals(updatedAuthor.getDateOfBirth(), foundAuthor.get().getDateOfBirth()),
                () -> assertNull(foundAuthor.get().getDateOfDeath()),
                () -> assertNull(foundAuthor.get().getCountryCode()),
                () -> assertNull(foundAuthor.get().getBiography()),
                () -> assertTrue(foundAuthor.get().getUpdatedAt().isAfter(author.getUpdatedAt()))
        );
    }

    @Test
    public void findAuthorsBySortName() {
        var firstAuthor = Author.create(new Text("First"), new Text("Shared"), "Shared, Test",
                null, null, null, null);
        var secondAuthor = Author.create(new Text("Second"), new Text("Shared"), "Shared, Test",
                null, null, null, null);
        var otherAuthor = Author.create(new Text("Other"), new Text("Name"), "Name, Other",
                null, null, null, null);
        adapter.save(secondAuthor);
        adapter.save(firstAuthor);
        adapter.save(otherAuthor);

        var foundAuthors = adapter.findBySortName("Shared, Test");

        assertEquals(List.of(firstAuthor.getId(), secondAuthor.getId()),
                foundAuthors.stream().map(Author::getId).toList());
    }

    @Test
    public void deleteAuthorAndItsBookLinks() {
        var author = Author.create(new Text("Linked"), new Text("Author"), null,
                null, null, null, null);
        adapter.save(author);
        Id<Book> bookId = Id.generate();
        dslContext.insertInto(BOOK)
                .set(BOOK.ID, bookId.value())
                .set(BOOK.ISBN, "author-delete-" + bookId.value())
                .set(BOOK.TITLE, "Book with author")
                .execute();
        dslContext.insertInto(BOOK_AUTHOR)
                .set(BOOK_AUTHOR.BOOK_ID, bookId.value())
                .set(BOOK_AUTHOR.AUTHOR_ID, author.getId().value())
                .set(BOOK_AUTHOR.AUTHOR_ORDER, 1)
                .execute();

        adapter.delete(author.getId());

        assertAll(
                () -> assertTrue(adapter.findById(author.getId()).isEmpty()),
                () -> assertEquals(0, dslContext.fetchCount(BOOK_AUTHOR,
                        BOOK_AUTHOR.AUTHOR_ID.eq(author.getId().value()))),
                () -> assertEquals(1, dslContext.fetchCount(BOOK,
                        BOOK.ID.eq(bookId.value())))
        );
    }
}
