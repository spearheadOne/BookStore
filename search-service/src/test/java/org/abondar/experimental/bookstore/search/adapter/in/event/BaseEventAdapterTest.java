package org.abondar.experimental.bookstore.search.adapter.in.event;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.abondar.experimental.bookstore.search.book.adapter.in.event.BookDecoder;
import org.abondar.experimental.bookstore.search.book.adapter.in.event.BookEventAdapter;
import org.abondar.experimental.bookstore.search.book.adapter.out.BookOpenSearchAdapter;
import org.abondar.experimental.bookstore.search.book.application.service.BookMetadataService;
import org.abondar.experimental.bookstore.search.catalog.adapter.in.event.CatalogDecoder;
import org.abondar.experimental.bookstore.search.catalog.adapter.in.event.CatalogEventAdapter;
import org.abondar.experimental.bookstore.search.catalog.adapter.out.CatalogOpenSearchAdapter;
import org.abondar.experimental.bookstore.search.catalog.application.service.CatalogService;
import org.abondar.experimental.bookstore.search.config.KafkaConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.abondar.experimental.bookstore.search.config.TopicProperties;
import org.abondar.experimental.bookstore.search.index.adapter.out.IndexOpenSearchAdapter;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseEventAdapterTest {

  private static final String TOPIC = "book-events-" + UUID.randomUUID();
  private static final String CATALOG_TOPIC = "catalog-events-" + UUID.randomUUID();
  protected static final String CATALOG_INDEX = "test-catalog";
  private static final String GROUP = "search-test-" + UUID.randomUUID();
  protected static final String METADATA_INDEX = "test-book-metadata";

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer("apache/kafka:4.3.2").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

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

  private static AnnotationConfigApplicationContext context;
  private static Admin admin;
  private static KafkaTemplate<String, byte[]> template;
  protected static OpenSearchClient client;

  @BeforeAll
  protected static void startApplicationFlow() throws Exception {
    admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()));
    admin
        .createTopics(
            List.of(
                new NewTopic(TOPIC, 1, (short) 1),
                new NewTopic(TOPIC + ".DLT", 1, (short) 1),
                new NewTopic(CATALOG_TOPIC, 1, (short) 1),
                new NewTopic(CATALOG_TOPIC + ".DLT", 1, (short) 1)))
        .all()
        .get(10, TimeUnit.SECONDS);

    context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "testKafka",
                Map.ofEntries(
                    Map.entry("topic.book-topic", TOPIC),
                    Map.entry("topic.catalog-topic", CATALOG_TOPIC),
                    Map.entry("spring.kafka.bootstrap-servers", kafka.getBootstrapServers()),
                    Map.entry("spring.kafka.consumer.group-id", GROUP),
                    Map.entry("spring.kafka.consumer.auto-offset-reset", "earliest"),
                    Map.entry("spring.kafka.consumer.enable-auto-commit", "false"),
                    Map.entry(
                        "spring.kafka.consumer.key-deserializer",
                        "org.apache.kafka.common.serialization.StringDeserializer"),
                    Map.entry(
                        "spring.kafka.consumer.value-deserializer",
                        "org.apache.kafka.common.serialization.ByteArrayDeserializer"),
                    Map.entry("spring.kafka.listener.ack-mode", "record"),
                    Map.entry(
                        "spring.kafka.producer.key-serializer",
                        "org.apache.kafka.common.serialization.StringSerializer"),
                    Map.entry(
                        "spring.kafka.producer.value-serializer",
                        "org.apache.kafka.common.serialization.ByteArraySerializer"),
                    Map.entry("spring.kafka.producer.acks", "all"),
                    Map.entry("spring.kafka.producer.properties.enable.idempotence", "true"))));
    context.registerBean(
        TopicProperties.class, () -> new TopicProperties(TOPIC, CATALOG_TOPIC, ".DLT", 25L, 3L));
    context.registerBean(
        OpenSearchProperties.class,
        () ->
            new OpenSearchProperties(
                "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200),
                "",
                "",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                CATALOG_INDEX,
                METADATA_INDEX,
                "test-book-content",
                1,
                0));
    context.register(TestConfig.class);
    context.refresh();
    context.getBean(IndexOpenSearchAdapter.class).createIndexes();
    client = context.getBean(OpenSearchClient.class);
    @SuppressWarnings("unchecked")
    var producer = (KafkaTemplate<String, byte[]>) context.getBean(KafkaTemplate.class);
    template = producer;
  }

  @AfterAll
  protected static void closeApplicationFlow() {
    if (context != null) {
      context.close();
    }
    if (admin != null) {
      admin.close();
    }
  }

  protected void publishAndAwait(SpecificRecordBase event) throws Exception {
    var buffer =
        new BinaryMessageEncoder<SpecificRecordBase>(event.getSpecificData(), event.getSchema())
            .encode(event);
    var payload = new byte[buffer.remaining()];
    buffer.get(payload);
    boolean bookEvent = event.getSchema().getField("bookId") != null;
    var topic = bookEvent ? TOPIC : CATALOG_TOPIC;
    var key = event.get(bookEvent ? "bookId" : "catalogId").toString();
    var offset =
        template.send(topic, key, payload).get(10, TimeUnit.SECONDS).getRecordMetadata().offset();

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var committed =
                  admin
                      .listConsumerGroupOffsets(GROUP)
                      .partitionsToOffsetAndMetadata()
                      .get(5, TimeUnit.SECONDS)
                      .get(new TopicPartition(topic, 0));
              assertNotNull(committed);
              assertTrue(
                  committed.offset() > offset, "Listener must finish processing before assertions");
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    KafkaConfig.class,
    OpenSearchConfig.class,
    BookDecoder.class,
    BookEventAdapter.class,
    BookMetadataService.class,
    IndexOpenSearchAdapter.class,
    BookOpenSearchAdapter.class,
    CatalogOpenSearchAdapter.class,
    CatalogDecoder.class,
    CatalogEventAdapter.class,
    CatalogService.class
  })
  @ImportAutoConfiguration(KafkaAutoConfiguration.class)
  static class TestConfig {}
}
