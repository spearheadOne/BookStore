package org.abondar.experimental.bookstore.book.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.abondar.experimental.bookstore.book.application.exception.BookEventPublicationException;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.abondar.experimental.bookstore.config.TopicProperties;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookContentFormat;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.apache.avro.util.ClassSecurityValidator;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers(disabledWithoutDocker = true)
class BookEventAdapterTest {

  private static final String TOPIC = "bookstore.book-events";

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer("apache/kafka:4.3.2").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  private static DefaultKafkaProducerFactory<String, byte[]> producerFactory;
  private static KafkaTemplate<String, byte[]> kafkaTemplate;
  private static ClassSecurityValidator.ClassSecurityPredicate previousAvroTrust;
  private KafkaConsumer<String, byte[]> consumer;
  private BookEventAdapter adapter;

  @BeforeAll
  static void configureBrokerClients() throws Exception {
    previousAvroTrust = ClassSecurityValidator.getGlobal();
    var trustedEvents =
        ClassSecurityValidator.builder()
            .add(BookMetadataUpserted.class)
            .add(BookDeleted.class)
            .add(BookContentAvailable.class)
            .add(BookContentFormat.class)
            .build();
    ClassSecurityValidator.setGlobal(
        ClassSecurityValidator.composite(previousAvroTrust, trustedEvents));
    try (var admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
    }
    producerFactory =
        new DefaultKafkaProducerFactory<>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG,
                5000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                1000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                5000));
    kafkaTemplate = new KafkaTemplate<>(producerFactory);
  }

  @BeforeEach
  void setUp() {
    consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false));
    var partition = new TopicPartition(TOPIC, 0);
    consumer.assign(List.of(partition));
    consumer.seekToEnd(List.of(partition));
    consumer.position(partition);
    adapter =
        new BookEventAdapter(kafkaTemplate, new TopicProperties(TOPIC, "unused-catalog-topic"));
  }

  @AfterEach
  void closeConsumer() {
    if (consumer != null) {
      consumer.close();
    }
  }

  @AfterAll
  static void closeProducer() {
    if (producerFactory != null) {
      producerFactory.destroy();
    }
    if (previousAvroTrust != null) {
      ClassSecurityValidator.setGlobal(previousAvroTrust);
    }
  }

  @Test
  void publishesMetadataAsSingleObjectAvroWithBookKey() throws Exception {
    var catalogId = new Id<Catalog>(UUID.randomUUID());
    var book = book(catalogId, "Description");

    adapter.sendBookUpsert(book);

    var record = receive(book.getId());
    var event = BookMetadataUpserted.fromByteBuffer(ByteBuffer.wrap(record.value()));
    assertNotNull(event.getEventId());
    assertEquals(book.getUpdatedAt(), event.getOccurredAt());
    assertEquals(book.getId().value(), event.getBookId());
    assertEquals(book.getIsbn().value(), event.getIsbn());
    assertEquals(book.getTitle().value(), event.getTitle());
    assertEquals("Description", event.getDescription());
    assertEquals(catalogId.value(), event.getCatalogId());
  }

  @Test
  void publishesManyMetadataSnapshotsWithDistinctEventIds() throws Exception {
    var first = book(Id.generate(), "First description");
    var second = book(null, null);

    adapter.sendManyUpserts(List.of(first, second));

    var records = new java.util.ArrayList<ConsumerRecord<String, byte[]>>();
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (records.size() < 2 && System.nanoTime() < deadline) {
      consumer.poll(Duration.ofMillis(500)).forEach(records::add);
    }
    assertEquals(2, records.size());
    var firstEvent = BookMetadataUpserted.fromByteBuffer(ByteBuffer.wrap(records.get(0).value()));
    var secondEvent = BookMetadataUpserted.fromByteBuffer(ByteBuffer.wrap(records.get(1).value()));
    assertEquals(first.getId().value().toString(), records.get(0).key());
    assertEquals(second.getId().value().toString(), records.get(1).key());
    assertEquals(first.getId().value(), firstEvent.getBookId());
    assertEquals(second.getId().value(), secondEvent.getBookId());
    assertEquals(first.getCatalogId().value(), firstEvent.getCatalogId());
    assertEquals(first.getDescription(), firstEvent.getDescription());
    assertEquals(first.getUpdatedAt(), firstEvent.getOccurredAt());
    assertEquals(second.getUpdatedAt(), secondEvent.getOccurredAt());
    assertNull(secondEvent.getCatalogId());
    assertNull(secondEvent.getDescription());
    assertNotNull(firstEvent.getEventId());
    assertNotNull(secondEvent.getEventId());
    assertNotEquals(firstEvent.getEventId(), secondEvent.getEventId());
  }

  @Test
  void publishesMetadataWithoutCatalogOrDescription() throws Exception {
    var book = book(null, null);

    adapter.sendBookUpsert(book);

    var event = BookMetadataUpserted.fromByteBuffer(ByteBuffer.wrap(receive(book.getId()).value()));
    assertNull(event.getCatalogId());
    assertNull(event.getDescription());
  }

  @Test
  void publishesBookDeletion() throws Exception {
    Id<Book> id = Id.generate();
    var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    adapter.sendBookDeleted(id);

    var event = BookDeleted.fromByteBuffer(ByteBuffer.wrap(receive(id).value()));
    assertEquals(id.value(), event.getBookId());
    assertNotNull(event.getEventId());
    assertTrue(!event.getOccurredAt().isBefore(before));
    assertTrue(!event.getOccurredAt().isAfter(Instant.now()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"pdf", "epub", "rtf"})
  void publishesContentMetadataForEachFormat(String format) throws Exception {
    Id<Book> id = Id.generate();
    var key = id.value() + "/" + format + "/file." + format;
    var contentType = format.equals("epub") ? "application/epub+zip" : "application/" + format;
    var filename = "my-book." + format;

    adapter.sendBookContentAvailable(id, format, key, contentType, filename, 123);

    var event = BookContentAvailable.fromByteBuffer(ByteBuffer.wrap(receive(id).value()));
    assertNotNull(event.getEventId());
    assertNotNull(event.getOccurredAt());
    assertEquals(id.value(), event.getBookId());
    assertEquals(format, event.getFormat().name());
    assertEquals(key, event.getStorageKey());
    assertEquals(contentType, event.getContentType());
    assertEquals(filename, event.getOriginalFilename());
    assertEquals(123L, event.getSizeBytes());
  }

  @Test
  void surfacesPublicationFailure() {
    var missingTopic = "missing-" + UUID.randomUUID();
    var missingTopicAdapter =
        new BookEventAdapter(kafkaTemplate, new TopicProperties(missingTopic, "unused"));

    var exception =
        assertThrows(
            BookEventPublicationException.class,
            () -> missingTopicAdapter.sendBookDeleted(Id.generate()));

    assertNotNull(exception.getCause());
  }

  @Test
  void publishesContentWithoutOriginalFilename() throws Exception {
    Id<Book> id = Id.generate();
    var key = id.value() + "/pdf/file.pdf";

    adapter.sendBookContentAvailable(id, "pdf", key, "application/pdf", null, 10);

    var event = BookContentAvailable.fromByteBuffer(ByteBuffer.wrap(receive(id).value()));
    assertNull(event.getOriginalFilename());
  }

  private ConsumerRecord<String, byte[]> receive(Id<Book> id) {
    var records = consumer.poll(Duration.ofSeconds(10));
    assertEquals(1, records.count());
    var record = records.iterator().next();
    assertEquals(TOPIC, record.topic());
    assertEquals(id.value().toString(), record.key());
    return record;
  }

  private static Book book(Id<Catalog> catalogId, String description) {
    return Book.restore(
        Id.generate(),
        new Isbn("0306406152"),
        new Text("Book"),
        description,
        null,
        List.of(),
        catalogId,
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-02-01T00:00:00Z"));
  }
}
