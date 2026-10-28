package org.abondar.experimental.bookstore.book.application.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
import java.util.List;
import org.abondar.experimental.bookstore.BaseRedisAdapterTest;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.adapter.out.BookCacheRedisAdapter;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(BookCacheRedisAdapter.class)
class BookCacheRedisAdapterTest extends BaseRedisAdapterTest {

  @Autowired private BookCacheRedisAdapter adapter;

  @Test
  void saveAndFindBook() {
    Id<Author> authorId = Id.generate();
    Id<Catalog> catalogId = Id.generate();
    var book =
        Book.create(
            new Isbn("0306406152"),
            new Text("A book"),
            "Description",
            Year.of(1980),
            List.of(authorId),
            catalogId);

    adapter.save(book);

    var restored = adapter.findById(book.getId()).orElseThrow();
    assertEquals(book.getId(), restored.getId());
    assertEquals(book.getIsbn(), restored.getIsbn());
    assertEquals(book.getTitle(), restored.getTitle());
    assertEquals(book.getDescription(), restored.getDescription());
    assertEquals(book.getIssueYear(), restored.getIssueYear());
    assertEquals(book.getAuthorIds(), restored.getAuthorIds());
    assertEquals(book.getCatalogId(), restored.getCatalogId());
    assertEquals(book.getCreatedAt(), restored.getCreatedAt());
    assertEquals(book.getUpdatedAt(), restored.getUpdatedAt());
    var ttl = redisTemplate.getExpire("book:v1:" + book.getId().value());
    assertTrue(ttl != null && ttl > 0 && ttl <= 30);
  }

  @Test
  void missingBook() {
    assertTrue(adapter.findById(Id.generate()).isEmpty());
  }

  @Test
  void saveReplacesCachedBook() {
    var book = Book.create(new Isbn("0306406152"), new Text("Old"), null, null, List.of(), null);
    var updated =
        Book.restore(
            book.getId(),
            book.getIsbn(),
            new Text("New"),
            null,
            null,
            List.of(),
            null,
            book.getCreatedAt(),
            book.getUpdatedAt());

    adapter.save(book);
    adapter.save(updated);

    assertEquals(new Text("New"), adapter.findById(book.getId()).orElseThrow().getTitle());
  }

  @Test
  void saveManyStoresAndReplacesBooks() {
    var first = Book.create(new Isbn("0306406152"), new Text("Old"), null, null, List.of(), null);
    var updated =
        Book.restore(
            first.getId(),
            first.getIsbn(),
            new Text("New"),
            null,
            null,
            List.of(),
            null,
            first.getCreatedAt(),
            first.getUpdatedAt());
    var second =
        Book.create(new Isbn("9780134685991"), new Text("Second"), null, null, List.of(), null);
    adapter.save(first);

    adapter.saveMany(List.of(updated, second));

    assertEquals(updated.getTitle(), adapter.findById(first.getId()).orElseThrow().getTitle());
    assertEquals(second.getTitle(), adapter.findById(second.getId()).orElseThrow().getTitle());
  }

  @Test
  void findByIdsPreservesOrderAndSkipsMissingBooks() {
    var first = Book.create(new Isbn("0306406152"), new Text("First"), null, null, List.of(), null);
    var second =
        Book.create(new Isbn("9780134685991"), new Text("Second"), null, null, List.of(), null);
    adapter.save(first);
    adapter.save(second);

    var found = adapter.findByIds(List.of(second.getId(), Id.generate(), first.getId()));

    assertEquals(List.of(second.getId(), first.getId()), found.stream().map(Book::getId).toList());
    assertTrue(adapter.findByIds(List.of()).isEmpty());
  }

  @Test
  void deleteBook() {
    var book = Book.create(new Isbn("0306406152"), new Text("A book"), null, null, List.of(), null);
    adapter.save(book);

    adapter.delete(book.getId());

    assertFalse(redisTemplate.hasKey("book:v1:" + book.getId().value()));
    assertTrue(adapter.findById(book.getId()).isEmpty());
  }

  @Test
  void deleteByIdsRemovesOnlyRequestedBooks() {
    var first = Book.create(new Isbn("0306406152"), new Text("First"), null, null, List.of(), null);
    var second =
        Book.create(new Isbn("9780134685991"), new Text("Second"), null, null, List.of(), null);
    var retained =
        Book.create(new Isbn("9780201633610"), new Text("Retained"), null, null, List.of(), null);
    adapter.saveMany(List.of(first, second, retained));

    adapter.deleteByIds(List.of(first.getId(), second.getId()));

    assertTrue(adapter.findById(first.getId()).isEmpty());
    assertTrue(adapter.findById(second.getId()).isEmpty());
    assertEquals(retained.getId(), adapter.findById(retained.getId()).orElseThrow().getId());
    adapter.deleteByIds(List.of());
    assertTrue(adapter.findById(retained.getId()).isPresent());
  }
}
