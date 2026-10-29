package org.abondar.experimental.bookstore.common;

import java.time.Duration;
import java.util.Optional;
import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

public abstract class BaseCacheRepository {
  protected final StringRedisTemplate redisTemplate;
  protected final JsonMapper jsonMapper;
  protected final CacheProperties cacheProperties;

  protected BaseCacheRepository(
      StringRedisTemplate redisTemplate, JsonMapper jsonMapper, CacheProperties cacheProperties) {
    this.redisTemplate = redisTemplate;
    this.jsonMapper = jsonMapper;
    this.cacheProperties = cacheProperties;
  }

  protected String cacheKey(String prefix, Id<?> id) {
    return prefix + id.value();
  }

  protected void cacheSave(String key, Object value, Duration ttl) {
    redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
  }

  protected <T> Optional<T> cacheFind(String key, Class<T> type) {
    var value = redisTemplate.opsForValue().get(key);
    return value == null ? Optional.empty() : Optional.of(jsonMapper.readValue(value, type));
  }

  protected void cacheDelete(String key) {
    redisTemplate.delete(key);
  }
}
