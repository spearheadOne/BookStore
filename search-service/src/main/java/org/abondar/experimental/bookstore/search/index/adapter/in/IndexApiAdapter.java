package org.abondar.experimental.bookstore.search.index.adapter.in;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.api.IndexApi;
import org.abondar.experimental.bookstore.search.index.application.port.in.CreateIndexUseCase;
import org.abondar.experimental.bookstore.search.index.application.port.in.DeleteIndexUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_search-admin')")
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
