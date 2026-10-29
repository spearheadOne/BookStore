package org.abondar.experimental.bookstore.catalog.application.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.abondar.experimental.bookstore.BaseRedisAdapterTest;
import org.abondar.experimental.bookstore.catalog.adapter.out.CatalogCacheRedisAdapter;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(CatalogCacheRedisAdapter.class)
class CatalogCacheRedisAdapterTest extends BaseRedisAdapterTest {

  @Autowired private CatalogCacheRedisAdapter adapter;

  @Test
  void saveAndFindCatalog() {
    var catalog = Catalog.create(new Text("Main catalog"));

    adapter.save(catalog);

    var restored = adapter.findById(catalog.getId()).orElseThrow();
    assertEquals(catalog.getId(), restored.getId());
    assertEquals(catalog.getName(), restored.getName());
    assertEquals(catalog.getCreatedAt(), restored.getCreatedAt());
    assertEquals(catalog.getUpdatedAt(), restored.getUpdatedAt());
    var ttl = redisTemplate.getExpire(key(catalog.getId()));
    assertTrue(ttl != null && ttl > 0 && ttl <= 30);
  }

  @Test
  void missingCatalog() {
    assertTrue(adapter.findById(Id.generate()).isEmpty());
  }

  @Test
  void saveReplacesCatalog() {
    var catalog = Catalog.create(new Text("Old name"));
    var updated =
        Catalog.restore(
            catalog.getId(), new Text("New name"), catalog.getCreatedAt(), catalog.getUpdatedAt());

    adapter.save(catalog);
    adapter.save(updated);

    assertEquals(updated.getName(), adapter.findById(catalog.getId()).orElseThrow().getName());
  }

  @Test
  void deleteCatalog() {
    var catalog = Catalog.create(new Text("To delete"));
    adapter.save(catalog);

    adapter.delete(catalog.getId());

    assertFalse(redisTemplate.hasKey(key(catalog.getId())));
    assertTrue(adapter.findById(catalog.getId()).isEmpty());
  }

  private String key(Id<Catalog> id) {
    return "catalog:v1:" + id.value();
  }
}
