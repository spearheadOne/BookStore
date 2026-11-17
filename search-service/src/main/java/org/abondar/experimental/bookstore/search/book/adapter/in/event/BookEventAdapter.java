package org.abondar.experimental.bookstore.search.book.adapter.in.event;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseEventAdapter;
import org.abondar.experimental.bookstore.search.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.UpsertBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookEventAdapter extends BaseEventAdapter {

  private final UpsertBookMetadataUseCase upsertBookMetadataUseCase;

  private final UpsertBookContentUseCase upsertBookContentUseCase;

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
      case "BookDeleted" -> deleteBookUseCase.deleteBook(bookId);
      case "BookContentAvailable" -> {
        var format = genericRecord.get("format").toString();
        var storageKey = genericRecord.get("storageKey").toString();
        var contentType = genericRecord.get("contentType").toString();
        var originalFileName =
            genericRecord.get("originalFilename") == null
                ? null
                : genericRecord.get("originalFilename").toString();
        var sizeBytes = Long.parseLong(genericRecord.get("sizeBytes").toString());
        var bookContent =
            new BookContent(bookId, format, storageKey, contentType, originalFileName, sizeBytes);

        upsertBookContentUseCase.upsertBookContent(bookContent);
      }
      default -> throw new IllegalArgumentException("Unsupported book event");
    }
  }
}
