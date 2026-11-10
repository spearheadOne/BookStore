package org.abondar.experimental.bookstore.book.adapter.out;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.book.application.exception.BookEventPublicationException;
import org.abondar.experimental.bookstore.book.application.port.out.BookEventRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.config.TopicProperties;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookContentFormat;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class BookEventAdapter implements BookEventRepository {

  private final KafkaTemplate<String, byte[]> kafkaTemplate;

  private final TopicProperties topicProperties;

  @Override
  public void sendBookUpsert(Book book) {
    var eventId = UUID.randomUUID();
    sendUpsert(eventId, book);
  }

  @Override
  public void sendManyUpserts(List<Book> books) {
    books.forEach(b -> sendUpsert(UUID.randomUUID(), b));
  }

  private void sendUpsert(UUID eventId, Book book) {
    var event =
        new BookMetadataUpserted(
            eventId,
            book.getUpdatedAt(),
            book.getId().value(),
            book.getIsbn().value(),
            book.getTitle().value(),
            book.getDescription(),
            book.getCatalogId() == null ? null : book.getCatalogId().value());

    sendEvent(event);
  }

  @Override
  public void sendBookDeleted(Id<Book> id) {
    var eventId = UUID.randomUUID();
    var event = new BookDeleted(eventId, Instant.now(), id.value());
    sendEvent(event);
  }

  @Override
  public void sendBookContentAvailable(
      Id<Book> id,
      String format,
      String storageKey,
      String contentType,
      String fileName,
      long size) {
    var eventId = UUID.randomUUID();
    var event =
        new BookContentAvailable(
            eventId,
            Instant.now(),
            id.value(),
            BookContentFormat.valueOf(format.toLowerCase(Locale.ROOT)),
            storageKey,
            contentType,
            fileName,
            size);
    sendEvent(event);
  }

  private void sendEvent(SpecificRecordBase event) {
    var bookId = (UUID) event.get("bookId");
    var eventId = (UUID) event.get("eventId");
    try {
      var encoder =
          new BinaryMessageEncoder<SpecificRecordBase>(event.getSpecificData(), event.getSchema());
      var encoded = encoder.encode(event);
      var payload = new byte[encoded.remaining()];
      encoded.get(payload);
      kafkaTemplate.send(topicProperties.bookTopic(), bookId.toString(), payload).join();
      log.debug("Published {} event {} for book {}", event.getSchema().getName(), eventId, bookId);
    } catch (IOException | RuntimeException exception) {
      throw new BookEventPublicationException(
          "Could not publish "
              + event.getSchema().getName()
              + " event "
              + eventId
              + " for book "
              + bookId,
          exception);
    }
  }
}
