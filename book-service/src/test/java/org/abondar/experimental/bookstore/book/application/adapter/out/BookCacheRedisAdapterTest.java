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
  void deleteBook() {
    var book = Book.create(new Isbn("0306406152"), new Text("A book"), null, null, List.of(), null);
    adapter.save(book);

    adapter.delete(book.getId());

    assertFalse(redisTemplate.hasKey("book:v1:" + book.getId().value()));
    assertTrue(adapter.findById(book.getId()).isEmpty());
  }
}
