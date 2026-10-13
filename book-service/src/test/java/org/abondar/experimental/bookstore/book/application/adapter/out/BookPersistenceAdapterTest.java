package org.abondar.experimental.bookstore.book.application.adapter.out;

import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.adapter.out.BookPersistenceAdapter;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Year;
import java.util.List;
import java.util.Set;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.AUTHOR;
import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.BOOK_AUTHOR;
import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.CATALOG;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
public class BookPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private BookPersistenceAdapter adapter;

    @Autowired
    private DSLContext dslContext;

    @Test
    public void saveAndFindBook() {
        var book = Book.create(
                new Isbn("0306406152"),
                new Text("test"),
                null,
                Year.of(1123),
                List.of(),
                null
        );

        adapter.save(book);
        var foundBook = adapter.findById(book.getId());

        assertTrue(foundBook.isPresent());

        assertAll(
                () -> assertEquals(book.getId(), foundBook.get().getId()),
                () -> assertEquals(book.getIsbn(), foundBook.get().getIsbn()),
                () -> assertEquals(book.getTitle(), foundBook.get().getTitle()),
                () -> assertEquals(book.getIssueYear(), foundBook.get().getIssueYear()),
                () -> assertEquals(List.of(), book.getAuthorIds()),
                () -> assertNull(book.getCatalogId()),
                () -> assertNotNull(book.getCreatedAt())
        );
    }

    @Test
    public void findBookByIsbn() {
        var book = Book.create(new Isbn("9780134685991"), new Text("Effective Java"),
                null, Year.of(2018), List.of(), null);

        adapter.save(book);
        var foundBook = adapter.findByIsbn(book.getIsbn());

        assertTrue(foundBook.isPresent());
        assertAll(
                () -> assertEquals(book.getId(), foundBook.get().getId()),
                () -> assertEquals(book.getIsbn(), foundBook.get().getIsbn()),
                () -> assertEquals(book.getTitle(), foundBook.get().getTitle())
        );
    }

    @Test
    public void findMissingBook() {
        assertAll(
                () -> assertTrue(adapter.findById(Id.<Book>generate()).isEmpty()),
                () -> assertTrue(adapter.findByIsbn(new Isbn("9780321125217")).isEmpty())
        );
    }

    @Test
    public void updateBookAndReplaceAuthors() {
        var firstAuthorId = insertAuthor("First");
        var secondAuthorId = insertAuthor("Second");
        var catalogId = insertCatalog("Updated books");
        var book = Book.create(new Isbn("9780201633610"), new Text("Original title"),
                null, Year.of(1994), List.of(firstAuthorId), null);
        adapter.save(book);

        var updatedBook = Book.restore(book.getId(), book.getIsbn(), new Text("Updated title"),
                "Updated description", Year.of(1995), List.of(secondAuthorId), catalogId,
                book.getCreatedAt(), book.getUpdatedAt().plusSeconds(1));
        adapter.save(updatedBook);
        var foundBook = adapter.findById(book.getId());

        assertTrue(foundBook.isPresent());
        assertAll(
                () -> assertEquals(updatedBook.getTitle(), foundBook.get().getTitle()),
                () -> assertEquals(updatedBook.getDescription(), foundBook.get().getDescription()),
                () -> assertEquals(updatedBook.getIssueYear(), foundBook.get().getIssueYear()),
                () -> assertEquals(List.of(secondAuthorId), foundBook.get().getAuthorIds()),
                () -> assertEquals(catalogId, foundBook.get().getCatalogId()),
                () -> assertEquals(1, dslContext.fetchCount(BOOK_AUTHOR,
                        BOOK_AUTHOR.BOOK_ID.eq(book.getId().value())))
        );
    }

    @Test
    public void findBooksByAuthor() {
        var firstAuthorId = insertAuthor("First");
        var secondAuthorId = insertAuthor("Second");
        var firstBook = Book.create(new Isbn("9780132350884"), new Text("First book"),
                null, null, List.of(firstAuthorId), null);
        var sharedBook = Book.create(new Isbn("9780306406157"), new Text("Shared book"),
                null, null, List.of(firstAuthorId, secondAuthorId), null);
        var otherBook = Book.create(new Isbn("9780131103627"), new Text("Other book"),
                null, null, List.of(secondAuthorId), null);
        adapter.save(firstBook);
        adapter.save(sharedBook);
        adapter.save(otherBook);

        var foundBooks = adapter.findByAuthor(firstAuthorId);

        assertAll(
                () -> assertEquals(Set.of(firstBook.getId(), sharedBook.getId()),
                        foundBooks.stream().map(Book::getId).collect(java.util.stream.Collectors.toSet())),
                () -> assertEquals(List.of(firstAuthorId, secondAuthorId),
                        foundBooks.stream().filter(book -> book.getId().equals(sharedBook.getId()))
                                .findFirst().orElseThrow().getAuthorIds())
        );
    }

    @Test
    public void findBookIdsByAuthor() {
        var authorId = insertAuthor("Selected");
        var otherAuthorId = insertAuthor("Other");
        var firstBook = Book.create(new Isbn("9780262033848"), new Text("First book"),
                null, null, List.of(authorId), null);
        var secondBook = Book.create(new Isbn("9780134757599"), new Text("Second book"),
                null, null, List.of(authorId, otherAuthorId), null);
        var otherBook = Book.create(new Isbn("9780596007126"), new Text("Other book"),
                null, null, List.of(otherAuthorId), null);
        adapter.save(firstBook);
        adapter.save(secondBook);
        adapter.save(otherBook);

        var foundIds = adapter.findIdsByAuthorId(authorId);

        assertEquals(Set.of(firstBook.getId(), secondBook.getId()), Set.copyOf(foundIds));
    }

    @Test
    public void findBooksByCatalog() {
        var catalogId = insertCatalog("Selected catalog");
        var otherCatalogId = insertCatalog("Other catalog");
        var firstBook = Book.create(new Isbn("9781491950357"), new Text("First book"),
                null, null, List.of(), catalogId);
        var secondBook = Book.create(new Isbn("9780134494166"), new Text("Second book"),
                null, null, List.of(), catalogId);
        var otherBook = Book.create(new Isbn("9781617294945"), new Text("Other book"),
                null, null, List.of(), otherCatalogId);
        adapter.save(firstBook);
        adapter.save(secondBook);
        adapter.save(otherBook);

        var foundBooks = adapter.findByCatalogId(catalogId);

        assertEquals(Set.of(firstBook.getId(), secondBook.getId()),
                foundBooks.stream().map(Book::getId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    public void deleteBookAndItsAuthorLinks() {
        var authorId = insertAuthor("Linked");
        var book = Book.create(new Isbn("9780321356680"), new Text("Book to delete"),
                null, null, List.of(authorId), null);
        adapter.save(book);

        adapter.delete(book.getId());

        assertAll(
                () -> assertTrue(adapter.findById(book.getId()).isEmpty()),
                () -> assertTrue(adapter.findIdsByAuthorId(authorId).isEmpty()),
                () -> assertEquals(0, dslContext.fetchCount(BOOK_AUTHOR,
                        BOOK_AUTHOR.BOOK_ID.eq(book.getId().value())))
        );
    }

    @Test
    public void saveManyBooks() {
        var firstAuthorId = insertAuthor("Bulk first");
        var secondAuthorId = insertAuthor("Bulk second");
        var catalogId = insertCatalog("Bulk catalog");
        var firstBook = Book.create(new Isbn("9781449331818"), new Text("First book"),
                null, null, List.of(firstAuthorId, secondAuthorId), catalogId);
        var secondBook = Book.create(new Isbn("9781491900864"), new Text("Second book"),
                null, null, List.of(secondAuthorId), null);

        adapter.saveMany(List.of(firstBook, secondBook));

        var foundFirstBook = adapter.findById(firstBook.getId());
        var foundSecondBook = adapter.findById(secondBook.getId());

        assertTrue(foundFirstBook.isPresent());
        assertTrue(foundSecondBook.isPresent());
        assertAll(
                () -> assertEquals(List.of(firstAuthorId, secondAuthorId), foundFirstBook.get().getAuthorIds()),
                () -> assertEquals(catalogId, foundFirstBook.get().getCatalogId()),
                () -> assertEquals(List.of(secondAuthorId), foundSecondBook.get().getAuthorIds()),
                () -> assertNull(foundSecondBook.get().getCatalogId())
        );
    }

    @Test
    public void saveManyRollsBackAllBooksWhenOneFails() {
        var firstBook = Book.create(new Isbn("9780321125217"), new Text("First book"),
                null, null, List.of(), null);
        var secondBook = Book.create(new Isbn("9780135957059"), new Text("Second book"),
                null, null, List.of(Id.<Author>generate()), null);

        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.saveMany(List.of(firstBook, secondBook)));

        assertAll(
                () -> assertTrue(adapter.findById(firstBook.getId()).isEmpty()),
                () -> assertTrue(adapter.findById(secondBook.getId()).isEmpty())
        );
    }

    private Id<Author> insertAuthor(String firstName) {
        Id<Author> id = Id.generate();
        dslContext.insertInto(AUTHOR)
                .set(AUTHOR.ID, id.value())
                .set(AUTHOR.FIRST_NAME, firstName)
                .set(AUTHOR.LAST_NAME, "Author")
                .execute();
        return id;
    }

    private Id<Catalog> insertCatalog(String name) {
        Id<Catalog> id = Id.generate();
        dslContext.insertInto(CATALOG)
                .set(CATALOG.ID, id.value())
                .set(CATALOG.NAME, name)
                .execute();
        return id;
    }

}
