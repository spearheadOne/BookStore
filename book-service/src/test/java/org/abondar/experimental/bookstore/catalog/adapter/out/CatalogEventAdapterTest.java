package org.abondar.experimental.bookstore.catalog.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogEventPublicationException;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.abondar.experimental.bookstore.config.TopicProperties;
import org.abondar.experimental.bookstore.events.avro.CatalogDeleted;
import org.abondar.experimental.bookstore.events.avro.CatalogUpserted;
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
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers(disabledWithoutDocker = true)
class CatalogEventAdapterTest {

  private static final String TOPIC = "bookstore.catalog-events";

  @Container
  static final KafkaContainer kafka =
      new KafkaContainer("apache/kafka:4.3.2").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  private static DefaultKafkaProducerFactory<String, byte[]> producerFactory;
  private static KafkaTemplate<String, byte[]> kafkaTemplate;
  private static ClassSecurityValidator.ClassSecurityPredicate previousAvroTrust;
  private KafkaConsumer<String, byte[]> consumer;
  private CatalogEventAdapter adapter;

  @BeforeAll
  static void configureBrokerClients() throws Exception {
    previousAvroTrust = ClassSecurityValidator.getGlobal();
    var trustedEvents =
        ClassSecurityValidator.builder()
            .add(CatalogUpserted.class)
            .add(CatalogDeleted.class)
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
        new CatalogEventAdapter(kafkaTemplate, new TopicProperties("unused-book-topic", TOPIC));
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
  void publishesCatalogSnapshotAsKeyedSingleObjectAvro() throws Exception {
    var catalog =
        Catalog.restore(
            Id.generate(),
            new Text("Renamed catalog"),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-02-01T00:00:00Z"));

    adapter.sendCatalogUpserted(catalog);

    var event = CatalogUpserted.fromByteBuffer(ByteBuffer.wrap(receive(catalog.getId()).value()));
    assertNotNull(event.getEventId());
    assertEquals(catalog.getUpdatedAt(), event.getOccurredAt());
    assertEquals(catalog.getId().value(), event.getCatalogId());
    assertEquals("Renamed catalog", event.getName());
  }

  @Test
  void publishesCatalogDeletion() throws Exception {
    Id<Catalog> id = Id.generate();
    var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    adapter.sendCatalogDeleted(id);

    var event = CatalogDeleted.fromByteBuffer(ByteBuffer.wrap(receive(id).value()));
    assertEquals(id.value(), event.getCatalogId());
    assertNotNull(event.getEventId());
    assertTrue(!event.getOccurredAt().isBefore(before));
    assertTrue(!event.getOccurredAt().isAfter(Instant.now()));
  }

  @Test
  void surfacesPublicationFailure() {
    var missingTopicAdapter =
        new CatalogEventAdapter(
            kafkaTemplate, new TopicProperties("unused", "missing-" + UUID.randomUUID()));

    var exception =
        assertThrows(
            CatalogEventPublicationException.class,
            () -> missingTopicAdapter.sendCatalogDeleted(Id.generate()));

    assertNotNull(exception.getCause());
  }

  private ConsumerRecord<String, byte[]> receive(Id<Catalog> id) {
    var records = consumer.poll(Duration.ofSeconds(10));
    assertEquals(1, records.count());
    var record = records.iterator().next();
    assertEquals(TOPIC, record.topic());
    assertEquals(id.value().toString(), record.key());
    return record;
  }
}
