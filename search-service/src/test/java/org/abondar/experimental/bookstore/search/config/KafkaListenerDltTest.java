package org.abondar.experimental.bookstore.search.config;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookContentFormat;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.abondar.experimental.bookstore.search.book.adapter.in.event.BookDecoder;
import org.abondar.experimental.bookstore.search.book.adapter.in.event.BookEventAdapter;
import org.abondar.experimental.bookstore.search.book.application.exception.BookEventException;
import org.abondar.experimental.bookstore.search.book.application.exception.ContentEventException;
import org.abondar.experimental.bookstore.search.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogEventException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers(disabledWithoutDocker = true)
class KafkaListenerDltTest {

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer("apache/kafka:4.3.2").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  private AnnotationConfigApplicationContext context;
  private Admin admin;
  private KafkaTemplate<String, byte[]> template;
  private UpsertBookMetadataUseCase upsert;
  private DeleteBookUseCase delete;
  private UpsertBookContentUseCase contentUpsert;
  private String topic;
  private String dlt;
  private String group;
  private final List<Integer> attempts = new CopyOnWriteArrayList<>();
  private CountDownLatch recoveryFailed;

  @BeforeEach
  void setUp() throws Exception {
    topic = "book-events-" + UUID.randomUUID();
    dlt = topic + ".DLT";
    group = "search-test-" + UUID.randomUUID();
    attempts.clear();
    recoveryFailed = new CountDownLatch(1);
    admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()));
    createTopic(topic);
    upsert = mock(UpsertBookMetadataUseCase.class);
    delete = mock(DeleteBookUseCase.class);
    contentUpsert = mock(UpsertBookContentUseCase.class);

    context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "testKafka",
                Map.ofEntries(
                    Map.entry("topic.book-topic", topic),
                    Map.entry("spring.kafka.bootstrap-servers", kafka.getBootstrapServers()),
                    Map.entry("spring.kafka.consumer.group-id", group),
                    Map.entry("spring.kafka.consumer.auto-offset-reset", "earliest"),
                    Map.entry("spring.kafka.consumer.enable-auto-commit", "false"),
                    Map.entry(
                        "spring.kafka.consumer.key-deserializer",
                        StringDeserializer.class.getName()),
                    Map.entry(
                        "spring.kafka.consumer.value-deserializer",
                        ByteArrayDeserializer.class.getName()),
                    Map.entry("spring.kafka.listener.ack-mode", "record"),
                    Map.entry(
                        "spring.kafka.producer.key-serializer",
                        "org.apache.kafka.common.serialization.StringSerializer"),
                    Map.entry(
                        "spring.kafka.producer.value-serializer",
                        "org.apache.kafka.common.serialization.ByteArraySerializer"),
                    Map.entry("spring.kafka.producer.acks", "all"),
                    Map.entry("spring.kafka.producer.properties.enable.idempotence", "true"),
                    Map.entry("spring.kafka.producer.properties.max.block.ms", "1000"),
                    Map.entry("spring.kafka.producer.properties.request.timeout.ms", "500"),
                    Map.entry("spring.kafka.producer.properties.delivery.timeout.ms", "1500"))));
    context.registerBean(
        TopicProperties.class, () -> new TopicProperties(topic, "unused", ".DLT", 25L, 3L));
    context.registerBean(UpsertBookMetadataUseCase.class, () -> upsert);
    context.registerBean(DeleteBookUseCase.class, () -> delete);
    context.registerBean(UpsertBookContentUseCase.class, () -> contentUpsert);
    context.register(TestConfig.class);
    context.refresh();
    @SuppressWarnings("unchecked")
    var producer = (KafkaTemplate<String, byte[]>) context.getBean(KafkaTemplate.class);
    template = producer;
    context
        .getBean(DefaultErrorHandler.class)
        .setRetryListeners(
            new RetryListener() {
              @Override
              public void failedDelivery(
                  ConsumerRecord<?, ?> record, Exception exception, int attempt) {
                attempts.add(attempt);
              }

              @Override
              public void recoveryFailed(
                  ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                recoveryFailed.countDown();
              }
            });
  }

  @AfterEach
  void closeClients() {
    if (context != null) {
      context.close();
    }
    if (admin != null) {
      admin.close();
    }
  }

  @Test
  void temporaryFailureRetriesThenSucceedsWithoutDlt() throws Exception {
    createTopic(dlt);
    doThrow(new UncheckedIOException(new IOException("Temporary outage")))
        .doNothing()
        .when(upsert)
        .upsertBookMetadata(any());
    var payload = metadata();

    var offset = send(payload);
    awaitCommitted(offset);

    verify(upsert, times(2)).upsertBookMetadata(any());
    assertEquals(List.of(1), attempts);
    try (var consumer = dltConsumer()) {
      assertTrue(consumer.poll(Duration.ofMillis(500)).isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {429, 503})
  void transientOpenSearchFailureRetriesThreeTimesThenPublishesDlt(int status) throws Exception {
    createTopic(dlt);
    doThrow(openSearchFailure(status)).when(upsert).upsertBookMetadata(any());
    var payload = metadata();

    var offset = send(payload);
    assertDlt(payload);
    awaitCommitted(offset);

    verify(upsert, times(4)).upsertBookMetadata(any());
    assertEquals(List.of(1, 2, 3, 4), attempts);
  }

  @Test
  void openSearchBadRequestGoesDirectlyToDlt() throws Exception {
    createTopic(dlt);
    doThrow(openSearchFailure(400)).when(upsert).upsertBookMetadata(any());
    var payload = metadata();

    var offset = send(payload);
    assertDlt(payload);
    awaitCommitted(offset);

    verify(upsert).upsertBookMetadata(any());
    assertEquals(List.of(1), attempts);
  }

  @ParameterizedTest
  @ValueSource(strings = {"book", "catalog"})
  void eventExceptionRetriesThenSucceeds(String kind) throws Exception {
    createTopic(dlt);
    RuntimeException failure =
        kind.equals("book")
            ? new BookEventException("Temporary failure", new IOException("Disconnected"))
            : new CatalogEventException("Temporary failure", new IOException("Disconnected"));
    doThrow(failure).doNothing().when(upsert).upsertBookMetadata(any());
    var offset = send(metadata());
    awaitCommitted(offset);
    verify(upsert, times(2)).upsertBookMetadata(any());
    assertEquals(List.of(1), attempts);
    try (var consumer = dltConsumer()) {
      assertTrue(consumer.poll(Duration.ofMillis(500)).isEmpty());
    }
  }

  @ParameterizedTest
  @CsvSource({"book,400", "book,503", "catalog,400", "catalog,503"})
  void wrappedOpenSearchFailureRetainsStatusPolicy(String kind, int status) throws Exception {
    createTopic(dlt);
    RuntimeException failure =
        kind.equals("book")
            ? new BookEventException("Indexing failed", openSearchFailure(status))
            : new CatalogEventException("Indexing failed", openSearchFailure(status));
    doThrow(failure).when(upsert).upsertBookMetadata(any());
    var payload = metadata();
    var offset = send(payload);
    assertDlt(payload);
    awaitCommitted(offset);
    int deliveries = status == 400 ? 1 : 4;
    verify(upsert, times(deliveries)).upsertBookMetadata(any());
    assertEquals(status == 400 ? List.of(1) : List.of(1, 2, 3, 4), attempts);
  }

  @Test
  void invalidContentGoesDirectlyToDlt() throws Exception {
    createTopic(dlt);
    doThrow(new ContentEventException("Invalid document", new TikaException("Corrupt PDF")))
        .when(contentUpsert)
        .upsertBookContent(any());
    var payload = content();
    var offset = send(payload);
    assertDlt(payload);
    awaitCommitted(offset);
    verify(contentUpsert).upsertBookContent(any());
    assertEquals(List.of(1), attempts);
  }

  @Test
  void transientContentFailureRetriesThenPublishesDlt() throws Exception {
    createTopic(dlt);
    doThrow(new BookEventException("S3 unavailable", new IOException("Disconnected")))
        .when(contentUpsert)
        .upsertBookContent(any());
    var payload = content();
    var offset = send(payload);
    assertDlt(payload);
    awaitCommitted(offset);
    verify(contentUpsert, times(4)).upsertBookContent(any());
    assertEquals(List.of(1, 2, 3, 4), attempts);
  }

  private byte[] content() throws IOException {
    var id = UUID.randomUUID();
    var event =
        new BookContentAvailable(
            UUID.randomUUID(),
            Instant.now(),
            id,
            BookContentFormat.pdf,
            id + "/pdf/file.pdf",
            "application/pdf",
            null,
            100L);
    var buffer = event.toByteBuffer();
    var payload = new byte[buffer.remaining()];
    buffer.get(payload);
    return payload;
  }

  @Test
  void malformedPayloadGoesDirectlyToDlt() throws Exception {
    createTopic(dlt);
    var payload = new byte[] {1, 2, 3};

    var offset = send(payload);
    assertDlt(payload);
    awaitCommitted(offset);

    assertEquals(List.of(1), attempts);
    verifyNoInteractions(upsert, delete);
  }

  @Test
  void failedDltPublicationDoesNotCommitOriginalRecord() throws Exception {
    // DLT deliberately absent and broker auto-creation disabled.
    doThrow(openSearchFailure(400)).when(upsert).upsertBookMetadata(any());
    var payload = metadata();
    var offset = send(payload);

    assertTrue(recoveryFailed.await(20, TimeUnit.SECONDS), "Expected failed DLT publication");
    var partition = new TopicPartition(topic, 0);
    var committed =
        admin
            .listConsumerGroupOffsets(group)
            .partitionsToOffsetAndMetadata()
            .get(5, TimeUnit.SECONDS)
            .get(partition);
    assertTrue(
        committed == null || committed.offset() <= offset,
        "Original record must remain uncommitted when DLT publication fails");

    createTopic(dlt);
    assertDlt(payload);
    awaitCommitted(offset);
    assertTrue(attempts.size() >= 2, "Original record should be redelivered");
  }

  private long send(byte[] payload) throws Exception {
    return template
        .send(topic, "book-key", payload)
        .get(10, TimeUnit.SECONDS)
        .getRecordMetadata()
        .offset();
  }

  private void assertDlt(byte[] payload) {
    try (var consumer = dltConsumer()) {
      var records = new ArrayList<ConsumerRecord<String, byte[]>>();
      await()
          .atMost(Duration.ofSeconds(20))
          .until(
              () -> {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
                return !records.isEmpty();
              });
      assertEquals(1, records.size());
      var record = records.getFirst();
      assertEquals(dlt, record.topic());
      assertEquals(0, record.partition());
      assertEquals("book-key", record.key());
      assertArrayEquals(payload, record.value());
      assertEquals(
          topic,
          new String(
              record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
              StandardCharsets.UTF_8));
      assertNotNull(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN));
    }
  }

  private KafkaConsumer<String, byte[]> dltConsumer() {
    var consumer =
        new KafkaConsumer<String, byte[]>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false));
    var partition = new TopicPartition(dlt, 0);
    consumer.assign(List.of(partition));
    consumer.seekToBeginning(List.of(partition));
    return consumer;
  }

  private void awaitCommitted(long offset) {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              var committed =
                  admin
                      .listConsumerGroupOffsets(group)
                      .partitionsToOffsetAndMetadata()
                      .get(5, TimeUnit.SECONDS)
                      .get(new TopicPartition(topic, 0));
              assertNotNull(committed);
              assertEquals(offset + 1, committed.offset());
            });
  }

  private void createTopic(String name) throws Exception {
    admin.createTopics(List.of(new NewTopic(name, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
  }

  private byte[] metadata() throws IOException {
    var event =
        new BookMetadataUpserted(
            UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "0306406152", "Book", null, null);
    var buffer = event.toByteBuffer();
    var payload = new byte[buffer.remaining()];
    buffer.get(payload);
    return payload;
  }

  private OpenSearchException openSearchFailure(int status) {
    return new OpenSearchException(
        new ErrorResponse.Builder()
            .status(status)
            .error(
                error ->
                    error
                        .type(status == 400 ? "mapper_parsing_exception" : "unavailable")
                        .reason("Test failure"))
            .build());
  }

  @Configuration(proxyBeanMethods = false)
  @Import({KafkaConfig.class, BookEventAdapter.class, BookDecoder.class})
  @ImportAutoConfiguration(KafkaAutoConfiguration.class)
  static class TestConfig {}
}
