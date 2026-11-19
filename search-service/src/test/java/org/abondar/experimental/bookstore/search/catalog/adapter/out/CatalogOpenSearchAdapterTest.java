package org.abondar.experimental.bookstore.search.catalog.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.adapter.out.BaseOpenSearchAdapterTest;
import org.abondar.experimental.bookstore.search.book.adapter.out.BookOpenSearchAdapter;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogEventException;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogSearchException;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.CatalogSearchQuery;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;

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
    var book =
        new BookMetadataDocument(UUID.randomUUID().toString(), "0306406152", "Book", null, id);
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
                BookMetadataDocument.class)
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
              CatalogEventException.class, () -> unavailable.upsertCatalog("id", "Catalog"));
      var deletion =
          assertThrows(CatalogEventException.class, () -> unavailable.deleteCatalog("id"));

      assertNotNull(upsert.getCause());
      assertNotNull(deletion.getCause());
      var search =
          assertThrows(
              CatalogSearchException.class,
              () -> unavailable.searchCatalogs(new CatalogSearchQuery(null, "Science", 20)));
      assertNotNull(search.getCause());
    }
  }

  @Test
  void searchesAnalyzedNameWithoutAnId() throws IOException {
    var science = saveCatalog("Science Fiction");
    var historical = saveCatalog("Historical Fiction");
    saveCatalog("Cooking");
    refreshCatalogs();

    var results = adapter.searchCatalogs(new CatalogSearchQuery(null, "FICTION", 20));

    assertEquals(Set.of(science, historical), Set.copyOf(results));
    assertEquals(2, results.size());
    assertTrue(adapter.searchCatalogs(new CatalogSearchQuery(null, "Fict", 20)).isEmpty());
  }

  @Test
  void searchesByIdAloneAndCombinesIdAndNameWithAnd() throws IOException {
    var science = saveCatalog("Science Fiction");
    saveCatalog("Cooking");
    refreshCatalogs();

    assertEquals(
        List.of(science),
        adapter.searchCatalogs(new CatalogSearchQuery(science.catalogId(), null, 20)));
    assertEquals(
        List.of(science),
        adapter.searchCatalogs(new CatalogSearchQuery(science.catalogId(), "fiction", 20)));
    assertTrue(
        adapter
            .searchCatalogs(new CatalogSearchQuery(science.catalogId(), "Cooking", 20))
            .isEmpty());
  }

  @Test
  void unknownIdAndNameReturnEmptyResults() throws IOException {
    saveCatalog("Science Fiction");
    refreshCatalogs();

    assertTrue(
        adapter
            .searchCatalogs(new CatalogSearchQuery(UUID.randomUUID().toString(), null, 20))
            .isEmpty());
    assertTrue(adapter.searchCatalogs(new CatalogSearchQuery(null, "missingword", 20)).isEmpty());
  }

  @Test
  void appliesLimitsAboveAndBelowTheOpenSearchDefault() throws IOException {
    for (int i = 0; i < 18; i++) {
      saveCatalog("Science " + i);
    }
    refreshCatalogs();

    assertEquals(15, adapter.searchCatalogs(new CatalogSearchQuery(null, null, 15)).size());
    assertEquals(1, adapter.searchCatalogs(new CatalogSearchQuery(null, "Science", 1)).size());
  }

  @Test
  void missingIndexSearchRetainsTheOpenSearchCause() throws IOException {
    client.indices().delete(request -> request.index(properties.catalogIndex()));

    var failure =
        assertThrows(
            CatalogSearchException.class,
            () -> adapter.searchCatalogs(new CatalogSearchQuery(null, null, 20)));

    assertEquals(404, ((OpenSearchException) failure.getCause()).status());
  }

  private CatalogDocument saveCatalog(String name) {
    var document = new CatalogDocument(UUID.randomUUID().toString(), name);
    adapter.upsertCatalog(document.catalogId(), document.name());
    return document;
  }

  private void refreshCatalogs() throws IOException {
    client.indices().refresh(request -> request.index(properties.catalogIndex()));
  }

  private Map<?, ?> findCatalog(String id) throws IOException {
    var result = client.get(request -> request.index(properties.catalogIndex()).id(id), Map.class);
    assertTrue(result.found());
    return result.source();
  }
}
