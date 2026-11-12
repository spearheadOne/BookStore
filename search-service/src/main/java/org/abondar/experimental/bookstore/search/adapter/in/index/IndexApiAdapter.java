package org.abondar.experimental.bookstore.search.adapter.in.index;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.api.IndexApi;
import org.abondar.experimental.bookstore.search.application.port.in.CreateIndexUseCase;
import org.abondar.experimental.bookstore.search.application.port.in.DeleteIndexUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IndexApiAdapter implements IndexApi {

  private final CreateIndexUseCase createIndexUseCase;

  private final DeleteIndexUseCase deleteIndexUseCase;

  @Override
  public ResponseEntity<Void> createIndexes() {
    createIndexUseCase.createIndexes();
    return ResponseEntity.status(201).build();
  }

  @Override
  public ResponseEntity<Void> deleteIndexes() {
    deleteIndexUseCase.deleteIndexes();
    return ResponseEntity.status(204).build();
  }
}
