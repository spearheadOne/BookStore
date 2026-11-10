package org.abondar.experimental.bookstore.catalog.adapter.out;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogEventPublicationException;
import org.abondar.experimental.bookstore.catalog.application.port.out.CatalogEventRepository;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.config.TopicProperties;
import org.abondar.experimental.bookstore.events.avro.CatalogDeleted;
import org.abondar.experimental.bookstore.events.avro.CatalogUpserted;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class CatalogEventAdapter implements CatalogEventRepository {

  private final KafkaTemplate<String, byte[]> kafkaTemplate;

  private final TopicProperties topicProperties;

  @Override
  public void sendCatalogUpserted(Catalog catalog) {
    var eventId = UUID.randomUUID();
    var event =
        new CatalogUpserted(
            eventId, catalog.getUpdatedAt(), catalog.getId().value(), catalog.getName().value());
    sendEvent(event);
  }

  @Override
  public void sendCatalogDeleted(Id<Catalog> catalogId) {
    var eventId = UUID.randomUUID();
    var event = new CatalogDeleted(eventId, Instant.now(), catalogId.value());
    sendEvent(event);
  }

  private void sendEvent(SpecificRecordBase event) {
    var catalogId = (UUID) event.get("catalogId");
    var eventId = (UUID) event.get("eventId");

    try {
      var encoder =
          new BinaryMessageEncoder<SpecificRecordBase>(event.getSpecificData(), event.getSchema());
      var encoded = encoder.encode(event);
      var payload = new byte[encoded.remaining()];
      encoded.get(payload);
      kafkaTemplate.send(topicProperties.catalogTopic(), catalogId.toString(), payload).join();
      log.debug(
          "Published {} event {} for catalog {}", event.getSchema().getName(), eventId, catalogId);
    } catch (IOException | RuntimeException exception) {
      throw new CatalogEventPublicationException(
          "Could not publish "
              + event.getSchema().getName()
              + " event "
              + eventId
              + " for catalog "
              + catalogId,
          exception);
    }
  }
}
