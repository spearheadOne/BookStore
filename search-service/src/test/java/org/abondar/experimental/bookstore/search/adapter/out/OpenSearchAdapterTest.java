package org.abondar.experimental.bookstore.search.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.application.exception.IndexCreationFailedException;
import org.abondar.experimental.bookstore.search.application.exception.IndexDeletionFailedException;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.transport.OpenSearchTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OpenSearchAdapterTest {

  @Container
  static final GenericContainer<?> opensearch =
      new GenericContainer<>("opensearchproject/opensearch:3.9.0")
          .withEnv("discovery.type", "single-node")
          .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
          .withEnv("DISABLE_SECURITY_PLUGIN", "true")
          .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200)
          .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200))
          .withStartupTimeout(Duration.ofMinutes(3));

  private static OpenSearchTransport transport;
  private static OpenSearchClient client;
  private OpenSearchProperties properties;
  private OpenSearchAdapter adapter;

  @BeforeAll
  static void connect() {
    var config = new OpenSearchConfig();
    transport = config.openSearchTransport(properties(endpoint(), "connection"));
    client = config.openSearchClient(transport);
  }

  @AfterAll
  static void closeClient() throws IOException {
    if (transport != null) {
      transport.close();
    }
  }

  @BeforeEach
  void setUp() {
    properties = properties(endpoint(), "test-" + UUID.randomUUID());
    adapter = new OpenSearchAdapter(properties, client);
  }

  @Test
  void createsAllConfiguredIndexes() throws IOException {
    adapter.createIndexes();
    for (var index : properties.getIndexNames()) {
      assertTrue(client.indices().exists(request -> request.index(index)).value());
    }
  }

  @Test
  void createsBookMetadataFieldMappings() throws IOException {
    adapter.createIndexes();

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
    adapter.createIndexes();

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
    adapter.createIndexes();

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

    new OpenSearchAdapter(custom, client).createIndexes();

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
    adapter.createIndexes();
    client.index(
        request ->
            request
                .index(properties.bookMetadataIndex())
                .id("book-1")
                .document(Map.of("title", "Existing book")));

    adapter.createIndexes();

    var book =
        client.get(
            request -> request.index(properties.bookMetadataIndex()).id("book-1"), Map.class);
    assertTrue(book.found());
    assertEquals("Existing book", book.source().get("title"));
  }

  @Test
  void createsMissingIndexesWhenSomeAlreadyExist() throws IOException {
    client.indices().create(request -> request.index(properties.bookMetadataIndex()));
    adapter.createIndexes();
    for (var index : properties.getIndexNames()) {
      assertTrue(client.indices().exists(request -> request.index(index)).value());
    }
  }

  @Test
  void deletesConfiguredIndexesAndPreservesUnrelatedIndex() throws IOException {
    adapter.createIndexes();
    var unrelated = "unrelated-" + UUID.randomUUID();
    client.indices().create(request -> request.index(unrelated));

    adapter.deleteIndexes();
    adapter.deleteIndexes();

    for (var index : properties.getIndexNames()) {
      assertFalse(client.indices().exists(request -> request.index(index)).value());
    }
    assertTrue(client.indices().exists(request -> request.index(unrelated)).value());
  }

  @Test
  void deletesPartiallyExistingIndexes() throws IOException {
    client.indices().create(request -> request.index(properties.catalogIndex()));
    adapter.deleteIndexes();
    assertFalse(
        client.indices().exists(request -> request.index(properties.catalogIndex())).value());
  }

  @Test
  void creationFailurePreservesOpenSearchCause() {
    var invalid = new OpenSearchAdapter(properties(endpoint(), "INVALID"), client);
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
          new OpenSearchAdapter(properties, new OpenSearchClient(unavailableTransport));
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

  private static String endpoint() {
    return "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200);
  }

  private static OpenSearchProperties properties(String url, String prefix) {
    return new OpenSearchProperties(
        url,
        "",
        "",
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        prefix + "-catalog",
        prefix + "-metadata",
        prefix + "-content",
        1,
        0);
  }
}
