package org.abondar.experimental.bookstore.author.application.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.abondar.experimental.bookstore.BaseRedisAdapterTest;
import org.abondar.experimental.bookstore.author.adapter.out.AuthorCacheRedisAdapter;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(AuthorCacheRedisAdapter.class)
class AuthorCacheRedisAdapterTest extends BaseRedisAdapterTest {

  @Autowired private AuthorCacheRedisAdapter adapter;

  @Test
  void saveAndFindAuthor() {
    var author =
        Author.create(
            new Text("Ada"),
            new Text("Lovelace"),
            "Lovelace, Ada",
            LocalDate.of(1815, 12, 10),
            LocalDate.of(1852, 11, 27),
            "GB",
            "Mathematician");

    adapter.save(author);

    var restored = adapter.findById(author.getId()).orElseThrow();
    assertEquals(author.getId(), restored.getId());
    assertEquals(author.getFirstName(), restored.getFirstName());
    assertEquals(author.getLastName(), restored.getLastName());
    assertEquals(author.getSortName(), restored.getSortName());
    assertEquals(author.getDateOfBirth(), restored.getDateOfBirth());
    assertEquals(author.getDateOfDeath(), restored.getDateOfDeath());
    assertEquals(author.getCountryCode(), restored.getCountryCode());
    assertEquals(author.getBiography(), restored.getBiography());
    assertEquals(author.getCreatedAt(), restored.getCreatedAt());
    assertEquals(author.getUpdatedAt(), restored.getUpdatedAt());
    var ttl = redisTemplate.getExpire(key(author.getId()));
    assertTrue(ttl != null && ttl > 0 && ttl <= 30);
  }

  @Test
  void missingAuthor() {
    assertTrue(adapter.findById(Id.generate()).isEmpty());
  }

  @Test
  void saveReplacesAuthorAndHandlesOptionalFields() {
    var author =
        Author.create(
            new Text("Ada"), new Text("Lovelace"), "Lovelace, Ada", null, null, null, null);
    var updated =
        Author.restore(
            author.getId(),
            new Text("Augusta"),
            author.getLastName(),
            "Lovelace, Augusta",
            null,
            null,
            null,
            null,
            author.getCreatedAt(),
            author.getUpdatedAt());

    adapter.save(author);
    adapter.save(updated);

    var restored = adapter.findById(author.getId()).orElseThrow();
    assertEquals(updated.getFirstName(), restored.getFirstName());
    assertEquals(updated.getSortName(), restored.getSortName());
    assertNull(restored.getDateOfBirth());
    assertNull(restored.getBiography());
  }

  @Test
  void deleteAuthor() {
    var author = Author.create(new Text("Ada"), new Text("Lovelace"), null, null, null, null, null);
    adapter.save(author);

    adapter.delete(author.getId());

    assertFalse(redisTemplate.hasKey(key(author.getId())));
    assertTrue(adapter.findById(author.getId()).isEmpty());
    adapter.delete(author.getId());
  }

  private String key(Id<Author> id) {
    return "author:id:v1:" + id.value();
  }
}
