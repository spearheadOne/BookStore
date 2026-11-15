package org.abondar.experimental.bookstore.search.adapter.in.event;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookContentFormat;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.abondar.experimental.bookstore.search.adapter.in.event.book.BookDecoder;
import org.abondar.experimental.bookstore.search.adapter.in.event.book.BookEventAdapter;
import org.abondar.experimental.bookstore.search.adapter.out.OpenSearchAdapter;
import org.abondar.experimental.bookstore.search.application.service.BookMetadataService;
import org.abondar.experimental.bookstore.search.config.KafkaConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.abondar.experimental.bookstore.search.config.TopicProperties;
import org.abondar.experimental.bookstore.search.domain.BookMetadata;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
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
class BookEventAdapterTest {

  private static final String TOPIC = "book-events-" + UUID.randomUUID();
  private static final String GROUP = "search-test-" + UUID.randomUUID();
  private static final String METADATA_INDEX = "test-book-metadata";

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer("apache/kafka:4.3.2").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

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

  private static AnnotationConfigApplicationContext context;
  private static Admin admin;
  private static KafkaTemplate<String, byte[]> template;
  private static OpenSearchClient client;

  @BeforeAll
  static void startApplicationFlow() throws Exception {
    admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()));
    admin
        .createTopics(
            List.of(new NewTopic(TOPIC, 1, (short) 1), new NewTopic(TOPIC + ".DLT", 1, (short) 1)))
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
        TopicProperties.class, () -> new TopicProperties(TOPIC, "unused", ".DLT", 25L, 3L));
    context.registerBean(
        OpenSearchProperties.class,
        () ->
            new OpenSearchProperties(
                "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200),
                "",
                "",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                "test-catalog",
                METADATA_INDEX,
                "test-book-content",
                1,
                0));
    context.register(TestConfig.class);
    context.refresh();
    context.getBean(OpenSearchAdapter.class).createIndexes();
    client = context.getBean(OpenSearchClient.class);
    @SuppressWarnings("unchecked")
    var producer = (KafkaTemplate<String, byte[]>) context.getBean(KafkaTemplate.class);
    template = producer;
  }

  @AfterAll
  static void closeApplicationFlow() {
    if (context != null) {
      context.close();
    }
    if (admin != null) {
      admin.close();
    }
  }

  @Test
  void metadataEventIsConsumedAndIndexed() throws Exception {
    var id = UUID.randomUUID();
    var catalog = UUID.randomUUID();

    publishAndAwait(metadata(id, "Book", "Description", catalog));

    assertEquals(
        new BookMetadata(id.toString(), "0306406152", "Book", "Description", catalog.toString()),
        find(id));
  }

  @Test
  void metadataEventPreservesNullOptionalFields() throws Exception {
    var id = UUID.randomUUID();

    publishAndAwait(metadata(id, "Book", null, null));

    assertEquals(new BookMetadata(id.toString(), "0306406152", "Book", null, null), find(id));
  }

  @Test
  void laterSnapshotReplacesMetadataAndClearsOptionalFields() throws Exception {
    var id = UUID.randomUUID();
    publishAndAwait(metadata(id, "Original title", "Description", UUID.randomUUID()));

    publishAndAwait(metadata(id, "Updated title", null, null));

    assertEquals(
        new BookMetadata(id.toString(), "0306406152", "Updated title", null, null), find(id));
  }

  @Test
  void duplicateEventKeepsOneMetadataDocument() throws Exception {
    var id = UUID.randomUUID();
    var event = metadata(id, "Book", null, null);

    publishAndAwait(event);
    publishAndAwait(event);

    client.indices().refresh(request -> request.index(METADATA_INDEX));
    var count =
        client.count(
            request ->
                request
                    .index(METADATA_INDEX)
                    .query(
                        query ->
                            query.term(
                                term -> term.field("bookId").value(FieldValue.of(id.toString())))));
    assertEquals(1L, count.count());
    assertEquals(new BookMetadata(id.toString(), "0306406152", "Book", null, null), find(id));
  }

  @Test
  void repeatedDeletionRemovesMetadataAndPreservesOtherBooks() throws Exception {
    var id = UUID.randomUUID();
    var otherId = UUID.randomUUID();
    publishAndAwait(metadata(id, "Delete this", null, null));
    publishAndAwait(metadata(otherId, "Keep this", null, null));
    var deletion = new BookDeleted(UUID.randomUUID(), Instant.now(), id);

    publishAndAwait(deletion);
    publishAndAwait(deletion);

    assertFalse(
        client
            .get(request -> request.index(METADATA_INDEX).id(id.toString()), BookMetadata.class)
            .found());
    assertEquals(
        new BookMetadata(otherId.toString(), "0306406152", "Keep this", null, null), find(otherId));
  }

  @Test
  void contentEventDoesNotCreateMetadataDocument() throws Exception {
    var id = UUID.randomUUID();
    var event =
        new BookContentAvailable(
            UUID.randomUUID(),
            Instant.now(),
            id,
            BookContentFormat.pdf,
            id + "/pdf/file.pdf",
            "application/pdf",
            "book.pdf",
            100L);

    publishAndAwait(event);

    assertFalse(
        client
            .get(request -> request.index(METADATA_INDEX).id(id.toString()), BookMetadata.class)
            .found());
  }

  private void publishAndAwait(SpecificRecordBase event) throws Exception {
    var buffer =
        new BinaryMessageEncoder<SpecificRecordBase>(event.getSpecificData(), event.getSchema())
            .encode(event);
    var payload = new byte[buffer.remaining()];
    buffer.get(payload);
    var offset =
        template
            .send(TOPIC, event.get("bookId").toString(), payload)
            .get(10, TimeUnit.SECONDS)
            .getRecordMetadata()
            .offset();

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var committed =
                  admin
                      .listConsumerGroupOffsets(GROUP)
                      .partitionsToOffsetAndMetadata()
                      .get(5, TimeUnit.SECONDS)
                      .get(new TopicPartition(TOPIC, 0));
              assertNotNull(committed);
              assertTrue(
                  committed.offset() > offset, "Listener must finish processing before assertions");
            });
  }

  private BookMetadata find(UUID id) throws Exception {
    var result =
        client.get(request -> request.index(METADATA_INDEX).id(id.toString()), BookMetadata.class);
    assertTrue(result.found());
    return result.source();
  }

  private BookMetadataUpserted metadata(UUID id, String title, String description, UUID catalogId) {
    return new BookMetadataUpserted(
        UUID.randomUUID(), Instant.now(), id, "0306406152", title, description, catalogId);
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    KafkaConfig.class,
    OpenSearchConfig.class,
    BookDecoder.class,
    BookEventAdapter.class,
    BookMetadataService.class,
    OpenSearchAdapter.class
  })
  @ImportAutoConfiguration(KafkaAutoConfiguration.class)
  static class TestConfig {}
}
