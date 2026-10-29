package org.abondar.experimental.bookstore.book.adapter.out;

import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.IntegerOutput;
import java.nio.charset.StandardCharsets;
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
import org.abondar.experimental.bookstore.common.BaseCacheRepository;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class BookCacheRedisAdapter extends BaseCacheRepository implements BookCacheRepository {

  private static final String KEY_PREFIX = "book:v1:";

  public BookCacheRedisAdapter(
      StringRedisTemplate redisTemplate, JsonMapper jsonMapper, CacheProperties cacheProperties) {
    super(redisTemplate, jsonMapper, cacheProperties);
  }

  private String key(Id<Book> id) {
    return cacheKey(KEY_PREFIX, id);
  }

  @Override
  public void save(Book book) {
    cacheSave(key(book.getId()), toCachedBook(book), cacheProperties.bookTtl());
  }

  @Override
  public void saveMany(List<Book> books) {
    if (books.isEmpty()) {
      return;
    }

    var cachedBooks = new HashMap<String, String>();
    for (Book b : books) {
      var cached = toCachedBook(b);
      cachedBooks.put(key(b.getId()), jsonMapper.writeValueAsString(cached));
    }

    var arguments = new ArrayList<byte[]>();
    arguments.add(Integer.toString(cachedBooks.size()).getBytes(StandardCharsets.UTF_8));
    cachedBooks.forEach(
        (bookKey, value) -> {
          arguments.add(bookKey.getBytes(StandardCharsets.UTF_8));
          arguments.add(value.getBytes(StandardCharsets.UTF_8));
        });
    arguments.add("PX".getBytes(StandardCharsets.UTF_8));
    arguments.add(
        Long.toString(cacheProperties.bookTtl().toMillis()).getBytes(StandardCharsets.UTF_8));

    var result =
        redisTemplate.execute(
            connection -> {
              var lettuceConnection = (LettuceConnection) connection;
              return (Long)
                  lettuceConnection.execute(
                      "MSETEX",
                      new IntegerOutput<>(ByteArrayCodec.INSTANCE),
                      arguments.toArray(byte[][]::new));
            },
            true);
    if (!Long.valueOf(1).equals(result)) {
      throw new IllegalStateException("Redis did not cache all books");
    }
  }

  @Override
  public Optional<Book> findById(Id<Book> id) {
    return cacheFind(key(id), CachedBook.class).map(cached -> toBook(id, cached));
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
    cacheDelete(key(id));
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
