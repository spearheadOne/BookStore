package org.abondar.experimental.bookstore.catalog.adapter.out;

import java.time.Instant;
import java.util.Optional;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogCacheRepository;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.BaseCacheRepository;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CatalogCacheRedisAdapter extends BaseCacheRepository
    implements CatalogCacheRepository {

  private static final String KEY_PREFIX = "catalog:v1:";

  public CatalogCacheRedisAdapter(
      StringRedisTemplate redisTemplate, JsonMapper jsonMapper, CacheProperties cacheProperties) {
    super(redisTemplate, jsonMapper, cacheProperties);
  }

  private String key(Id<Catalog> id) {
    return cacheKey(KEY_PREFIX, id);
  }

  @Override
  public void save(Catalog catalog) {
    var cached =
        new CachedCatalog(
            catalog.getName().value(), catalog.getCreatedAt(), catalog.getUpdatedAt());
    cacheSave(key(catalog.getId()), cached, cacheProperties.catalogTtl());
  }

  @Override
  public Optional<Catalog> findById(Id<Catalog> id) {
    return cacheFind(key(id), CachedCatalog.class).map(cached -> toCatalog(id, cached));
  }

  @Override
  public void delete(Id<Catalog> id) {
    cacheDelete(key(id));
  }

  private Catalog toCatalog(Id<Catalog> id, CachedCatalog cachedCatalog) {
    return Catalog.restore(
        id, new Text(cachedCatalog.name()), cachedCatalog.createdAt(), cachedCatalog.updatedAt());
  }

  private record CachedCatalog(String name, Instant createdAt, Instant updatedAt) {}
}
