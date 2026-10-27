package org.abondar.experimental.bookstore.book.adapter.out;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class BookCacheRedisAdapter implements BookCacheRepository {

  private static final String KEY_PREFIX = "book:v1:";

  private final StringRedisTemplate redisTemplate;
  private final JsonMapper jsonMapper;
  private final Duration ttl;

  public BookCacheRedisAdapter(
      StringRedisTemplate redisTemplate,
      JsonMapper jsonMapper,
      @Value("${book.cache.ttl}") Duration ttl) {
    this.redisTemplate = redisTemplate;
    this.jsonMapper = jsonMapper;
    this.ttl = ttl;
  }

  private static String key(Id<Book> id) {
    return KEY_PREFIX + id.value();
  }

  @Override
  public void save(Book book) {
    var cached =
        new CachedBook(
            book.getIsbn().value(),
            book.getTitle().value(),
            book.getDescription(),
            book.getIssueYear() == null ? null : book.getIssueYear().getValue(),
            book.getAuthorIds().stream().map(Id::value).toList(),
            book.getCatalogId() == null ? null : book.getCatalogId().value(),
            book.getCreatedAt(),
            book.getUpdatedAt());
    redisTemplate.opsForValue().set(key(book.getId()), jsonMapper.writeValueAsString(cached), ttl);
  }

  @Override
  public Optional<Book> findById(Id<Book> id) {
    var value = redisTemplate.opsForValue().get(key(id));
    if (value == null) {
      return Optional.empty();
    }
    var cached = jsonMapper.readValue(value, CachedBook.class);
    return Optional.of(
        Book.restore(
            id,
            new Isbn(cached.isbn()),
            new Text(cached.title()),
            cached.description(),
            cached.issueYear() == null ? null : Year.of(cached.issueYear()),
            cached.authorIds().stream().map(authorId -> new Id<Author>(authorId)).toList(),
            cached.catalogId() == null ? null : new Id<Catalog>(cached.catalogId()),
            cached.createdAt(),
            cached.updatedAt()));
  }

  @Override
  public void delete(Id<Book> id) {
    redisTemplate.delete(key(id));
  }

  private record CachedBook(
      String isbn,
      String title,
      String description,
      Integer issueYear,
      List<UUID> authorIds,
      UUID catalogId,
      Instant createdAt,
      Instant updatedAt) {}
}
