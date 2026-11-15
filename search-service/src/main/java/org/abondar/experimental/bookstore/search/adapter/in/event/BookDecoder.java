package org.abondar.experimental.bookstore.search.adapter.in.event;

import java.io.IOException;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;
import org.springframework.stereotype.Component;

@Component
public class BookDecoder {
  private final BinaryMessageDecoder<GenericRecord> binaryMessageDecoder;

  public BookDecoder() {
    this.binaryMessageDecoder = new BinaryMessageDecoder<>(GenericData.get(), null);

    binaryMessageDecoder.addSchema(BookMetadataUpserted.getClassSchema());
    binaryMessageDecoder.addSchema(BookDeleted.getClassSchema());
    binaryMessageDecoder.addSchema(BookContentAvailable.getClassSchema());
  }

  public GenericRecord decode(byte[] data) throws IOException {
    return binaryMessageDecoder.decode(data);
  }
}
