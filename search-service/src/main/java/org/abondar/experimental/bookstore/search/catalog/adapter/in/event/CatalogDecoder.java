package org.abondar.experimental.bookstore.search.catalog.adapter.in.event;

import org.abondar.experimental.bookstore.events.avro.CatalogDeleted;
import org.abondar.experimental.bookstore.events.avro.CatalogUpserted;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseDecoder;
import org.springframework.stereotype.Component;

@Component
public class CatalogDecoder extends BaseDecoder {
  public CatalogDecoder() {
    binaryMessageDecoder.addSchema(CatalogUpserted.getClassSchema());
    binaryMessageDecoder.addSchema(CatalogDeleted.getClassSchema());
  }
}
