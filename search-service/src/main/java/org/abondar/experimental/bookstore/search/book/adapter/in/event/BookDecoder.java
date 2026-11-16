package org.abondar.experimental.bookstore.search.book.adapter.in.event;

import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseDecoder;
import org.springframework.stereotype.Component;

@Component
public class BookDecoder extends BaseDecoder {

  public BookDecoder() {

    binaryMessageDecoder.addSchema(BookMetadataUpserted.getClassSchema());
    binaryMessageDecoder.addSchema(BookDeleted.getClassSchema());
    binaryMessageDecoder.addSchema(BookContentAvailable.getClassSchema());
  }
}
