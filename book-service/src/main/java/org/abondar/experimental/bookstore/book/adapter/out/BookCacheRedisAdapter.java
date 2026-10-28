package org.abondar.experimental.bookstore.book.adapter.out;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.out.BookCacheRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
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

  private String key(Id<Book> id) {
    return KEY_PREFIX + id.value();
  }

  @Override
  public void save(Book book) {
    redisTemplate
        .opsForValue()
        .set(key(book.getId()), jsonMapper.writeValueAsString(toCachedBook(book)), ttl);
  }

  @Override
  public void saveMany(List<Book> books) {
    var cachedBooks = new HashMap<String, String>();
    for (Book b : books) {
      var cached = toCachedBook(b);
      cachedBooks.put(key(b.getId()), jsonMapper.writeValueAsString(cached));
    }

    redisTemplate.opsForValue().multiSet(cachedBooks);
  }

  @Override
  public Optional<Book> findById(Id<Book> id) {
    var value = redisTemplate.opsForValue().get(key(id));
    if (value == null) {
      return Optional.empty();
    }
    var cached = jsonMapper.readValue(value, CachedBook.class);
    return Optional.of(toBook(id, cached));
  }

  @Override
  public List<Book> findByIds(List<Id<Book>> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    var rawBooks = redisTemplate.opsForValue().multiGet(ids.stream().map(this::key).toList());
    var books = new ArrayList<Book>();
    for (int index = 0; index < ids.size(); index++) {
      var rawBook = rawBooks.get(index);
      if (rawBook != null) {
        books.add(toBook(ids.get(index), jsonMapper.readValue(rawBook, CachedBook.class)));
      }
    }
    return books;
  }

  @Override
  public void delete(Id<Book> id) {
    redisTemplate.delete(key(id));
  }

  @Override
  public void deleteByIds(List<Id<Book>> ids) {
    if (!ids.isEmpty()) {
      redisTemplate.delete(ids.stream().map(this::key).toList());
    }
  }

  private CachedBook toCachedBook(Book book) {
    return new CachedBook(
        book.getIsbn().value(),
        book.getTitle().value(),
        book.getDescription(),
        book.getIssueYear() == null ? null : book.getIssueYear().getValue(),
        book.getAuthorIds().stream().map(Id::value).toList(),
        book.getCatalogId() == null ? null : book.getCatalogId().value(),
        book.getCreatedAt(),
        book.getUpdatedAt());
  }

  private Book toBook(Id<Book> id, CachedBook cachedBook) {
    return Book.restore(
        id,
        new Isbn(cachedBook.isbn()),
        new Text(cachedBook.title()),
        cachedBook.description(),
        cachedBook.issueYear() == null ? null : Year.of(cachedBook.issueYear()),
        cachedBook.authorIds().stream().map(rawId -> new Id<Author>(rawId)).toList(),
        cachedBook.catalogId() == null ? null : new Id<>(cachedBook.catalogId()),
        cachedBook.createdAt(),
        cachedBook.updatedAt());
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
