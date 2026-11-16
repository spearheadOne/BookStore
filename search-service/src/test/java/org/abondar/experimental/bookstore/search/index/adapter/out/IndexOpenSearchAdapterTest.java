package org.abondar.experimental.bookstore.search.index.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.adapter.out.BaseOpenSearchAdapterTest;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.abondar.experimental.bookstore.search.index.application.exception.IndexCreationFailedException;
import org.abondar.experimental.bookstore.search.index.application.exception.IndexDeletionFailedException;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;

class IndexOpenSearchAdapterTest extends BaseOpenSearchAdapterTest {

  @Test
  void createsAllConfiguredIndexes() throws IOException {
    indexAdapter.createIndexes();
    for (var index : properties.getIndexNames()) {
      assertTrue(client.indices().exists(request -> request.index(index)).value());
    }
  }

  @Test
  void createsBookMetadataFieldMappings() throws IOException {
    indexAdapter.createIndexes();

    var fields = mappings(properties.bookMetadataIndex());

    assertEquals(5, fields.size());
    assertTrue(fields.get("bookId").isKeyword());
    assertTrue(fields.get("isbn").isKeyword());
    assertTrue(fields.get("title").isText());
    assertTrue(fields.get("description").isText());
    assertTrue(fields.get("catalogId").isKeyword());
  }

  @Test
  void createsBookContentFieldMappingsWithFilenameKeywordSubfield() throws IOException {
    indexAdapter.createIndexes();

    var fields = mappings(properties.bookContentIndex());

    assertEquals(4, fields.size());
    assertTrue(fields.get("bookId").isKeyword());
    assertTrue(fields.get("format").isKeyword());
    assertTrue(fields.get("content").isText());
    var filename = fields.get("originalFilename");
    assertTrue(filename.isText());
    assertTrue(filename.text().fields().get("keyword").isKeyword());
  }

  @Test
  void createsCatalogFieldMappingsWithNameKeywordSubfield() throws IOException {
    indexAdapter.createIndexes();

    var fields = mappings(properties.catalogIndex());

    assertEquals(2, fields.size());
    assertTrue(fields.get("catalogId").isKeyword());
    var name = fields.get("name");
    assertTrue(name.isText());
    assertTrue(name.text().fields().get("keyword").isKeyword());
  }

  @Test
  void appliesConfiguredShardAndReplicaCountsToEveryIndex() throws IOException {
    var custom =
        new OpenSearchProperties(
            properties.url(),
            properties.username(),
            properties.password(),
            properties.connectTimeout(),
            properties.responseTimeout(),
            properties.catalogIndex(),
            properties.bookMetadataIndex(),
            properties.bookContentIndex(),
            2,
            2);

    new IndexOpenSearchAdapter(custom, client).createIndexes();

    for (var index : custom.getIndexNames()) {
      var settings =
          client
              .indices()
              .getSettings(request -> request.index(index))
              .result()
              .get(index)
              .settings()
              .index();
      assertEquals(2, settings.numberOfShards());
      assertEquals(2, settings.numberOfReplicas());
    }
  }

  @Test
  void repeatedCreationPreservesExistingDocuments() throws IOException {
    indexAdapter.createIndexes();
    client.index(
        request ->
            request
                .index(properties.bookMetadataIndex())
                .id("book-1")
                .document(Map.of("title", "Existing book")));

    indexAdapter.createIndexes();

    var book =
        client.get(
            request -> request.index(properties.bookMetadataIndex()).id("book-1"), Map.class);
    assertTrue(book.found());
    assertEquals("Existing book", book.source().get("title"));
  }

  @Test
  void createsMissingIndexesWhenSomeAlreadyExist() throws IOException {
    client.indices().create(request -> request.index(properties.bookMetadataIndex()));
    indexAdapter.createIndexes();
    for (var index : properties.getIndexNames()) {
      assertTrue(client.indices().exists(request -> request.index(index)).value());
    }
  }

  @Test
  void deletesConfiguredIndexesAndPreservesUnrelatedIndex() throws IOException {
    indexAdapter.createIndexes();
    var unrelated = "unrelated-" + UUID.randomUUID();
    client.indices().create(request -> request.index(unrelated));

    indexAdapter.deleteIndexes();
    indexAdapter.deleteIndexes();

    for (var index : properties.getIndexNames()) {
      assertFalse(client.indices().exists(request -> request.index(index)).value());
    }
    assertTrue(client.indices().exists(request -> request.index(unrelated)).value());
  }

  @Test
  void deletesPartiallyExistingIndexes() throws IOException {
    client.indices().create(request -> request.index(properties.catalogIndex()));
    indexAdapter.deleteIndexes();
    assertFalse(
        client.indices().exists(request -> request.index(properties.catalogIndex())).value());
  }

  @Test
  void creationFailurePreservesOpenSearchCause() {
    var invalid = new IndexOpenSearchAdapter(properties(endpoint(), "INVALID"), client);
    var exception = assertThrows(IndexCreationFailedException.class, invalid::createIndexes);
    assertNotNull(exception.getCause());
    assertTrue(exception.getMessage().contains("INVALID"));
  }

  @Test
  void connectionFailuresAreTranslatedForBothOperations() throws IOException {
    // Keep the port reserved but don't serve HTTP, giving a deterministic response timeout.
    try (var server = new ServerSocket(0);
        var unavailableTransport =
            new OpenSearchConfig()
                .openSearchTransport(
                    properties("http://localhost:" + server.getLocalPort(), "unavailable"))) {
      var unavailable =
          new IndexOpenSearchAdapter(properties, new OpenSearchClient(unavailableTransport));
      var creation = assertThrows(IndexCreationFailedException.class, unavailable::createIndexes);
      var deletion = assertThrows(IndexDeletionFailedException.class, unavailable::deleteIndexes);
      assertNotNull(creation.getCause());
      assertNotNull(deletion.getCause());
    }
  }

  private Map<String, Property> mappings(String index) throws IOException {
    return client
        .indices()
        .getMapping(request -> request.index(index))
        .result()
        .get(index)
        .mappings()
        .properties();
  }
}
