package org.abondar.experimental.bookstore.search.book.adapter.in.event;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseEventAdapter;
import org.abondar.experimental.bookstore.search.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookEventAdapter extends BaseEventAdapter {

  private final UpsertBookMetadataUseCase upsertBookMetadataUseCase;

  private final DeleteBookUseCase deleteBookUseCase;

  private final BookDecoder bookDecoder;

  @KafkaListener(topics = "${topic.book-topic}")
  public void handleBookEvents(byte[] event) throws IOException {
    var genericRecord = decodeEvent(event, bookDecoder);

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

        upsertBookMetadataUseCase.upsertBookMetadata(book);
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
