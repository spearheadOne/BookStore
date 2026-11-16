package org.abondar.experimental.bookstore.search.adapter.in.event;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;

@Slf4j
public abstract class BaseEventAdapter {

  protected GenericRecord decodeEvent(byte[] payload, BaseDecoder decoder) throws IOException {
    var record = decoder.decode(payload);
    log.info(
        "Received {} event {} occurred at {}",
        record.getSchema().getName(),
        record.get("eventId"),
        record.get("occurredAt"));
    return record;
  }
}
