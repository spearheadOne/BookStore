package org.abondar.experimental.bookstore.author.adapter.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.abondar.experimental.bookstore.author.application.port.out.AuthorCacheRepository;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.common.BaseCacheRepository;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AuthorCacheRedisAdapter extends BaseCacheRepository implements AuthorCacheRepository {

  private static final String ID_KEY_PREFIX = "author:id:v1:";

  public AuthorCacheRedisAdapter(
      StringRedisTemplate redisTemplate, JsonMapper jsonMapper, CacheProperties cacheProperties) {
    super(redisTemplate, jsonMapper, cacheProperties);
  }

  private String key(Id<Author> id) {
    return ID_KEY_PREFIX + id.value();
  }

  @Override
  public void save(Author author) {
    var idCached =
        new CachedAuthor(
            author.getFirstName().value(),
            author.getLastName().value(),
            author.getSortName(),
            author.getDateOfBirth(),
            author.getDateOfDeath(),
            author.getCountryCode(),
            author.getBiography(),
            author.getCreatedAt(),
            author.getUpdatedAt());

    redisTemplate
        .opsForValue()
        .set(
            key(author.getId()),
            jsonMapper.writeValueAsString(idCached),
            cacheProperties.authorIdTtl());
  }

  @Override
  public Optional<Author> findById(Id<Author> id) {
    var val = redisTemplate.opsForValue().get(key(id));
    if (val == null) {
      return Optional.empty();
    }

    return Optional.of(toAuthor(id, jsonMapper.readValue(val, CachedAuthor.class)));
  }

  @Override
  public void delete(Id<Author> id) {
    redisTemplate.delete(key(id));
  }

  private Author toAuthor(Id<Author> id, CachedAuthor cachedAuthor) {
    return Author.restore(
        id,
        new Text(cachedAuthor.firstName()),
        new Text(cachedAuthor.lastName()),
        cachedAuthor.sortName(),
        cachedAuthor.birthDate(),
        cachedAuthor.deathDate(),
        cachedAuthor.countryCode(),
        cachedAuthor.biography(),
        cachedAuthor.createdAt(),
        cachedAuthor.updatedAt());
  }

  private record CachedAuthor(
      String firstName,
      String lastName,
      String sortName,
      LocalDate birthDate,
      LocalDate deathDate,
      String countryCode,
      String biography,
      Instant createdAt,
      Instant updatedAt) {}
}
