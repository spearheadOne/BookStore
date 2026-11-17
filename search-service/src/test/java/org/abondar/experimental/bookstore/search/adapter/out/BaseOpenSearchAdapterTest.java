package org.abondar.experimental.bookstore.search.adapter.out;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.abondar.experimental.bookstore.search.index.adapter.out.IndexOpenSearchAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseOpenSearchAdapterTest {

  @Container
  static final GenericContainer<?> opensearch =
      new GenericContainer<>("opensearchproject/opensearch:3.9.0")
          .withEnv("discovery.type", "single-node")
          .withEnv("action.auto_create_index", "false")
          .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
          .withEnv("DISABLE_SECURITY_PLUGIN", "true")
          .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200)
          .waitingFor(Wait.forHttp("/_cluster/health").forStatusCode(200))
          .withStartupTimeout(Duration.ofMinutes(3));

  private static OpenSearchTransport transport;
  protected static OpenSearchClient client;
  protected OpenSearchProperties properties;
  protected IndexOpenSearchAdapter indexAdapter;

  @BeforeAll
  protected static void connect() {
    var config = new OpenSearchConfig();
    transport = config.openSearchTransport(properties(endpoint(), "connection"));
    client = config.openSearchClient(transport);
  }

  @AfterAll
  protected static void closeClient() throws IOException {
    if (transport != null) {
      transport.close();
    }
  }

  @BeforeEach
  protected void setUpIndexes() {
    properties = properties(endpoint(), "test-" + UUID.randomUUID());
    indexAdapter = new IndexOpenSearchAdapter(properties, client);
  }

  protected static String endpoint() {
    return "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200);
  }

  protected static OpenSearchProperties properties(String url, String prefix) {
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
