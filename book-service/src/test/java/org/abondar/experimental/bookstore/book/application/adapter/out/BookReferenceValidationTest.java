package org.abondar.experimental.bookstore.book.application.adapter.out;

import static org.abondar.experimental.bookstore.adapter.out.persistence.generated.Tables.AUTHOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.abondar.experimental.bookstore.BasePersistenceAdapterTest;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.adapter.out.BookPersistenceAdapter;
import org.abondar.experimental.bookstore.book.application.exception.InvalidBookReferenceException;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BookReferenceValidationTest extends BasePersistenceAdapterTest {

  @Autowired private BookPersistenceAdapter adapter;

  @ParameterizedTest
  @ValueSource(strings = {"author", "catalog"})
  void invalidReferenceRollsBackEntireUpdate(String relationship) {
    var existingAuthor = Id.<Author>generate();
    dslContext
        .insertInto(AUTHOR)
        .set(AUTHOR.ID, existingAuthor.value())
        .set(AUTHOR.FIRST_NAME, "Reference test")
        .set(AUTHOR.LAST_NAME, "Author")
        .execute();
    var original =
        Book.create(
            new Isbn("9781861972712"),
            new Text("Original"),
            "Original description",
            null,
            List.of(existingAuthor),
            null);
    adapter.save(original);
    var authors =
        relationship.equals("author") ? List.of(Id.<Author>generate()) : original.getAuthorIds();
    var catalog = relationship.equals("catalog") ? Id.<Catalog>generate() : null;
    var updated =
        Book.restore(
            original.getId(),
            original.getIsbn(),
            new Text("Changed"),
            "Changed description",
            null,
            authors,
            catalog,
            original.getCreatedAt(),
            original.getUpdatedAt().plusSeconds(1));

    var failure = assertThrows(InvalidBookReferenceException.class, () -> adapter.update(updated));

    assertTrue(failure.getCause() != null);
    var stored = adapter.findById(original.getId()).orElseThrow();
    assertEquals(original.getTitle(), stored.getTitle());
    assertEquals(original.getDescription(), stored.getDescription());
    assertEquals(original.getAuthorIds(), stored.getAuthorIds());
    assertEquals(original.getCatalogId(), stored.getCatalogId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"author", "catalog"})
  void invalidReferenceDoesNotLeaveInsertedBook(String relationship) {
    var authors =
        relationship.equals("author") ? List.of(Id.<Author>generate()) : List.<Id<Author>>of();
    var catalog = relationship.equals("catalog") ? Id.<Catalog>generate() : null;
    var book =
        Book.create(new Isbn("9781861972712"), new Text("Invalid"), null, null, authors, catalog);

    assertThrows(InvalidBookReferenceException.class, () -> adapter.save(book));

    assertTrue(adapter.findById(book.getId()).isEmpty());
  }

  @Test
  void invalidAuthorLinkPreservesExistingLinks() {
    var book =
        Book.create(new Isbn("9781861972712"), new Text("Book"), null, null, List.of(), null);
    adapter.save(book);

    assertThrows(
        InvalidBookReferenceException.class,
        () -> adapter.addAuthorLinks(List.of(book.withAuthor(Id.generate()))));

    assertTrue(adapter.findById(book.getId()).orElseThrow().getAuthorIds().isEmpty());
  }
}
