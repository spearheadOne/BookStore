package org.abondar.experimental.bookstore.search.catalog.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.adapter.out.BaseOpenSearchAdapterTest;
import org.abondar.experimental.bookstore.search.book.adapter.out.BookOpenSearchAdapter;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

class CatalogOpenSearchAdapterTest extends BaseOpenSearchAdapterTest {

  private CatalogOpenSearchAdapter adapter;

  @BeforeEach
  void setUpCatalogAdapter() {
    adapter = new CatalogOpenSearchAdapter(properties, client);
    indexAdapter.createIndexes();
  }

  @Test
  void upsertsStructuredCatalogDocument() throws IOException {
    var id = UUID.randomUUID().toString();

    adapter.upsertCatalog(id, "Science Fiction");

    assertEquals(Map.of("catalogId", id, "name", "Science Fiction"), findCatalog(id));
  }

  @Test
  void replacesCatalogNameWithoutCreatingDuplicateDocuments() throws IOException {
    var id = UUID.randomUUID().toString();
    adapter.upsertCatalog(id, "Original name");

    adapter.upsertCatalog(id, "Updated name");
    adapter.upsertCatalog(id, "Updated name");

    client.indices().refresh(request -> request.index(properties.catalogIndex()));
    assertEquals(1L, client.count(request -> request.index(properties.catalogIndex())).count());
    assertEquals("Updated name", findCatalog(id).get("name"));
  }

  @Test
  void deletingMissingCatalogCanBeRepeated() throws IOException {
    var id = UUID.randomUUID().toString();
    adapter.deleteCatalog(id);
    adapter.deleteCatalog(id);
    assertFalse(
        client.get(request -> request.index(properties.catalogIndex()).id(id), Map.class).found());
  }

  @Test
  void repeatedDeletionPreservesBooksAndOtherCatalogs() throws IOException {
    var id = UUID.randomUUID().toString();
    var other = UUID.randomUUID().toString();
    adapter.upsertCatalog(id, "Delete this");
    adapter.upsertCatalog(other, "Keep this");
    var book = new BookMetadata(UUID.randomUUID().toString(), "0306406152", "Book", null, id);
    new BookOpenSearchAdapter(properties, client).upsertBookMetadata(book);

    adapter.deleteCatalog(id);
    adapter.deleteCatalog(id);

    assertFalse(
        client.get(request -> request.index(properties.catalogIndex()).id(id), Map.class).found());
    assertEquals("Keep this", findCatalog(other).get("name"));
    assertTrue(
        client
            .get(
                request -> request.index(properties.bookMetadataIndex()).id(book.bookId()),
                BookMetadata.class)
            .found());
  }

  @Test
  void connectionFailuresKeepTheirCauses() throws IOException {
    try (var server = new ServerSocket(0);
        var unavailableTransport =
            new OpenSearchConfig()
                .openSearchTransport(
                    properties("http://localhost:" + server.getLocalPort(), "unavailable"))) {
      var unavailable =
          new CatalogOpenSearchAdapter(properties, new OpenSearchClient(unavailableTransport));

      var upsert =
          assertThrows(
              UncheckedIOException.class, () -> unavailable.upsertCatalog("id", "Catalog"));
      var deletion =
          assertThrows(UncheckedIOException.class, () -> unavailable.deleteCatalog("id"));

      assertNotNull(upsert.getCause());
      assertNotNull(deletion.getCause());
    }
  }

  private Map<?, ?> findCatalog(String id) throws IOException {
    var result = client.get(request -> request.index(properties.catalogIndex()).id(id), Map.class);
    assertTrue(result.found());
    return result.source();
  }
}
