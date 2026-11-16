package org.abondar.experimental.bookstore.search.catalog.adapter.in.event;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseEventAdapter;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.DeleteCatalogUseCase;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.UpsertCatalogUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogEventAdapter extends BaseEventAdapter {

  private final UpsertCatalogUseCase upsertCatalogUseCase;

  private final DeleteCatalogUseCase deleteCatalogUseCase;

  private final CatalogDecoder catalogDecoder;

  @KafkaListener(topics = "${topic.catalog-topic}")
  public void handleCatalogEvents(byte[] event) throws IOException {
    var genericRecord = decodeEvent(event, catalogDecoder);

    var catalogId = genericRecord.get("catalogId").toString();

    switch (genericRecord.getSchema().getName()) {
      case "CatalogUpserted" ->
          upsertCatalogUseCase.upsertCatalog(catalogId, genericRecord.get("name").toString());
      case "CatalogDeleted" -> deleteCatalogUseCase.deleteCatalog(catalogId);
      default -> throw new IllegalArgumentException("Unsupported catalog event");
    }
  }
}
