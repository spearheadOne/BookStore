package org.abondar.experimental.bookstore.search.adapter.in.event.book;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.application.port.in.AddBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.domain.BookMetadata;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookEventAdapter {

  private final AddBookMetadataUseCase addBookMetadaUseCase;

  private final DeleteBookUseCase deleteBookUseCase;

  private final BookDecoder bookDecoder;

  @KafkaListener(topics = "${topic.book-topic}")
  public void handleBookMetaDataEvent(byte[] event) throws IOException {
    var genericRecord = bookDecoder.decode(event);

    var eventId = genericRecord.get("eventId").toString();
    var occurredAt = genericRecord.get("occurredAt").toString();
    log.info("Received event with id {} and occurredAt at {}", eventId, occurredAt);

    var bookId = genericRecord.get("bookId").toString();

    switch (genericRecord.getSchema().getName()) {
      case "BookMetadataUpserted" -> {
        var isbn = genericRecord.get("isbn").toString();
        var title = genericRecord.get("title").toString();
        var description =
            genericRecord.get("description") == null
                ? null
                : genericRecord.get("description").toString();
        var catalogId =
            genericRecord.get("catalogId") == null
                ? null
                : genericRecord.get("catalogId").toString();

        var book = new BookMetadata(bookId, isbn, title, description, catalogId);

        addBookMetadaUseCase.upsertBookMetadata(book);
      }
      case "BookDeleted" -> {
        deleteBookUseCase.deleteBook(bookId);
      }
      case "BookContentAvailable" -> {
        // todo implement later in next card
      }
      default -> throw new IllegalArgumentException("Unsupported book event");
    }
  }
}
