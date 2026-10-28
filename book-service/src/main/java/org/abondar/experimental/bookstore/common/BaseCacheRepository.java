package org.abondar.experimental.bookstore.common;

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
}
