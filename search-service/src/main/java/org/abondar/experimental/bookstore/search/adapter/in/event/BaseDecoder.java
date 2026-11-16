package org.abondar.experimental.bookstore.search.adapter.in.event;

import java.io.IOException;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageDecoder;

public abstract class BaseDecoder {
  protected final BinaryMessageDecoder<GenericRecord> binaryMessageDecoder;

  protected BaseDecoder() {
    this.binaryMessageDecoder = new BinaryMessageDecoder<>(GenericData.get(), null);
  }

  public GenericRecord decode(byte[] data) throws IOException {
    return binaryMessageDecoder.decode(data);
  }
}
